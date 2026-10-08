package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ReturnApplyRequest;
import com.example.supermarket.dto.ReturnRequestResponse;
import com.example.supermarket.dto.ReturnReviewRequest;
import com.example.supermarket.dto.ReturnShipRequest;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.ReturnRequest;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.PaymentRecordRepository;
import com.example.supermarket.repository.ReturnRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 售后/退货退款流程（第 13 条：用户收到货不想要了要退货）。
 *
 * <p><b>为什么要有「寄回」这一段</b>：用户收到货发现不喜欢，退款不能让商家凭空亏货。
 * 完整链路是「申请 → 商家判责 → （若需）用户寄回 → 商家确认收货 → 退款」。
 * 原实现是「申请 → 商家同意 → 直接退款」，等于商家只要同意就得无条件退货款，
 * 这个口子开着，平台会被薅。
 *
 * <p><b>运费分责任</b>（用户已确认的方案）：由**商家在审核时判定**，不让用户自报 ——
 * 否则用户会全选「质量问题」骗取运费。
 * <ul>
 *   <li>{@code SELLER} 质量问题 / 错发 / 损坏 —— 且**一律不要求寄回**（几十块钱来回寄件不现实）</li>
 *   <li>{@code BUYER} 七天无理由「不想要了」—— 需寄回，用户自己承担运费</li>
 * </ul>
 *
 * <p><b>与 orders.refund_status 的关系</b>：orders 上那个三态字段是给订单列表快速筛选用 的，
 * 这里同步维护一份（APPLYING / APPROVED / REJECTED 三个值不变），
 * 中间态（待寄回 / 已寄回）只在 return_request 上，两边不冲突。
 */
@Service
public class ReturnRequestService {

    public static final String STATUS_APPLYING = "APPLYING";
    public static final String STATUS_WAITING_SHIP = "WAITING_SHIP";
    public static final String STATUS_SHIPPED_BACK = "SHIPPED_BACK";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String FREIGHT_SELLER = "SELLER";
    public static final String FREIGHT_BUYER = "BUYER";

    private static final Set<String> OPEN_STATUSES =
            Set.of(STATUS_APPLYING, STATUS_WAITING_SHIP, STATUS_SHIPPED_BACK);

    private final ProductRepository productRepository;
    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final WalletService walletService;
    private final AmountRecordService amountRecordService;
    private final MessageService msgService;

    public ReturnRequestService(ProductRepository productRepository,
                                ReturnRequestRepository returnRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                PaymentRecordRepository paymentRecordRepository,
                                WalletService walletService,
                                AmountRecordService amountRecordService,
                                MessageService msgService) {
        this.productRepository = productRepository;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.walletService = walletService;
        this.amountRecordService = amountRecordService;
        this.msgService = msgService;
    }

    // ==================== 用户端 ====================

    /**
     * 用户提交售后申请。
     *
     * <p>门禁与原 applyRefund 一致：仅 COMPLETED（已收货）可申请 ——
     * 货还没发就能退单不合常理，而真收到货有问题反而没处申诉。
     */
    @Transactional
    public ReturnRequestResponse apply(Long userId, ReturnApplyRequest req) {
        OrderEntity order = orderRepository.findByIdAndUserId(req.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        // COMPLETED 在 OrderService 里是 private，这里用字面量；改动需与 OrderService 同步
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(409,
                    "商品需要先确认收货，确认收货后如有质量问题可申请售后；未发货想退请直接取消订单。");
        }
        if (returnRepository.hasOpenRequest(userId)) {
            throw new BusinessException(409, "你已有一笔售后申请正在处理中，请等待商家处理完成。");
        }
        if (order.getRefundStatus() != null && !"NONE".equals(order.getRefundStatus())) {
            throw new BusinessException(409, "该订单已申请过售后，请勿重复提交。");
        }

        ReturnRequest r = new ReturnRequest();
        r.setUserId(userId);
        r.setOrderId(order.getId());
        r.setStatus(STATUS_APPLYING);
        r.setType(req.getType() == null || req.getType().isBlank() ? "REFUND" : req.getType());
        r.setRefundAmount(order.getPayAmount());
        r.setReason(req.getReason());
        r.setRemark(req.getRemark());
        r.setNeedReturn(false);          // 审核时才定
        ReturnRequest saved = returnRepository.save(r);

        // orders 上的三态同步一份，供订单列表按售后状态筛选
        order.setRefundStatus("APPLYING");
        order.setRefundReason(req.getReason());
        orderRepository.save(order);

        msgService.push(userId, UserMessage.TYPE_ORDER, "售后申请已提交",
                "订单 " + order.getOrderNo() + " 的售后申请已提交，商家正在审核。",
                "orderDetail", String.valueOf(order.getId()), "RETURN:APPLIED:" + saved.getId());

        return ReturnRequestResponse.from(saved, order.getOrderNo());
    }

    /** 用户填写寄回信息（快递公司 + 运单号） */
    @Transactional
    public ReturnRequestResponse shipBack(Long userId, Long returnId, ReturnShipRequest req) {
        ReturnRequest r = ownedBy(userId, returnId);
        if (!STATUS_WAITING_SHIP.equals(r.getStatus())) {
            throw new BusinessException(409, "当前状态不能填写寄回信息。");
        }
        r.setExpressCompany(req.getExpressCompany());
        r.setTrackingNo(req.getTrackingNo());
        r.setShippedBackAt(LocalDateTime.now());
        r.setStatus(STATUS_SHIPPED_BACK);
        ReturnRequest saved = returnRepository.save(r);

        OrderEntity order = orderRepository.findById(r.getOrderId()).orElseThrow();
        msgService.push(userId, UserMessage.TYPE_ORDER, "退货包裹已寄出",
                "商家确认收到后会为你退款。运单号：" + req.getTrackingNo(),
                "orderDetail", String.valueOf(order.getId()), "RETURN:SHIPPED:" + saved.getId());

        return ReturnRequestResponse.from(saved, order.getOrderNo());
    }

    /** 用户端：我的售后列表 */
    @Transactional(readOnly = true)
    public PageResponse<ReturnRequestResponse> listMine(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        List<ReturnRequest> rows = returnRepository.findPageByUserId(userId, pageable);
        Map<Long, String> nos = orderNosOf(rows);
        List<ReturnRequestResponse> items = rows.stream()
                .map(r -> ReturnRequestResponse.from(r, nos.get(r.getOrderId())))
                .toList();
        return PageResponse.of(items, page, size, returnRepository.countByUserId(userId));
    }

    /** 某订单的售后单（订单详情页展示用） */
    @Transactional(readOnly = true)
    public ReturnRequestResponse getByOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return returnRepository.findByOrderId(orderId).stream()
                .findFirst()
                .map(r -> ReturnRequestResponse.from(r, order.getOrderNo()))
                .orElse(null);
    }

    // ==================== 后台端 ====================

    /**
     * 商家审核。
     *
     * <p>三条分支：
     * <ol>
     *   <li>驳回 → REJECTED（必须给理由）</li>
     *   <li>同意且<b>不需寄回</b> → 直接 APPROVED 并退款（质量问题等）</li>
     *   <li>同意且<b>需寄回</b> → WAITING_SHIP，等用户填单号（此时**不放款**）</li>
     * </ol>
     *
     * <p>⚠️ 分支 3 是本条需求的核心：原先审核通过就直接退钱，等于「同意 = 白给」，
     * 商家毫无风控手段。
     */
    @Transactional
    public ReturnRequestResponse review(Long returnId, ReturnReviewRequest req) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        OrderEntity order = orderRepository.findById(r.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (r.getStatus() != null && !STATUS_APPLYING.equals(r.getStatus())) {
            throw new BusinessException(409, "该售后单已处理过，当前状态：" + r.getStatus());
        }
        boolean approved = Boolean.TRUE.equals(req.getApproved());

        if (!approved) {
            if (req.getRemark() == null || req.getRemark().isBlank()) {
                throw new BusinessException(400, "驳回必须填写理由。");
            }
            r.setStatus(STATUS_REJECTED);
            r.setAdminRemark(req.getRemark());
            returnRepository.save(r);
            order.setRefundStatus("REJECTED");
            order.setRefundRemark(req.getRemark());
            orderRepository.save(order);
            msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "售后申请未通过",
                    "很抱歉，你的售后申请未通过：" + req.getRemark(),
                    "orderDetail", String.valueOf(order.getId()), "RETURN:REJECT:" + r.getId());
            return ReturnRequestResponse.from(r, order.getOrderNo());
        }

        boolean needReturn = Boolean.TRUE.equals(req.getNeedReturn());
        r.setAdminRemark(req.getRemark());
        r.setApprovedAt(LocalDateTime.now());

        if (!needReturn) {
            // 质量问题等：不必寄回，直接放款
            r.setNeedReturn(false);
            r.setFreightBorneBy(FREIGHT_SELLER);
            r.setStatus(STATUS_APPROVED);
            r.setCompletedAt(LocalDateTime.now());
            returnRepository.save(r);
            refundNow(order);
            msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "退款已到账",
                    "订单 " + order.getOrderNo() + " 退款 " + order.getPayAmount() + " 元已退回钱包余额。",
                    "orderDetail", String.valueOf(order.getId()), "RETURN:APPROVED:" + r.getId());
            return ReturnRequestResponse.from(r, order.getOrderNo());
        }

        // 需寄回：先不放款，等用户寄 + 商家确认收货
        String borne = req.getFreightBorneBy();
        if (borne == null || (borne.isBlank())) {
            throw new BusinessException(400, "需要寄回时必须指定运费由谁承担（商家 / 用户）。");
        }
        if (!FREIGHT_SELLER.equals(borne) && !FREIGHT_BUYER.equals(borne)) {
            throw new BusinessException(400, "运费承担方只能是 SELLER 或 BUYER。");
        }
        r.setNeedReturn(true);
        r.setFreightBorneBy(borne);
        r.setStatus(STATUS_WAITING_SHIP);
        returnRepository.save(r);

        msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "售后申请已通过，请寄回商品",
                (FREIGHT_SELLER.equals(borne) ? "退货运费由商家承担。" : "退货运费需你自行承担。")
                        + "请填写快递公司与运单号，商家确认收到后即退款。",
                "orderDetail", String.valueOf(order.getId()), "RETURN:WAITSHIP:" + r.getId());

        return ReturnRequestResponse.from(r, order.getOrderNo());
    }

    /** 商家确认收到退货 → 放款（用户已寄回后的最后一步） */
    @Transactional
    public ReturnRequestResponse confirmReceived(Long returnId, String remark) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!STATUS_SHIPPED_BACK.equals(r.getStatus())) {
            throw new BusinessException(409, "当前状态不能确认收货。");
        }
        OrderEntity order = orderRepository.findById(r.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        r.setStatus(STATUS_APPROVED);
        r.setCompletedAt(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            r.setAdminRemark(remark);
        }
        returnRepository.save(r);
        refundNow(order);

        msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "退款已到账",
                "订单 " + order.getOrderNo() + " 退货已确认，退款 " + order.getPayAmount() + " 元已退回钱包余额。",
                "orderDetail", String.valueOf(order.getId()), "RETURN:COMPLETED:" + r.getId());

        return ReturnRequestResponse.from(r, order.getOrderNo());
    }

    /** 后台列表（按状态筛，空=全部） */
    @Transactional(readOnly = true)
    public PageResponse<ReturnRequestResponse> listAll(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        List<ReturnRequest> rows = (status == null || status.isBlank())
                ? returnRepository.findAllPaged(pageable)
                : returnRepository.findByStatus(status, pageable);
        long total = (status == null || status.isBlank())
                ? returnRepository.count() : returnRepository.countByStatus(status);
        Map<Long, String> nos = orderNosOf(rows);
        List<ReturnRequestResponse> items = rows.stream()
                .map(r -> ReturnRequestResponse.from(r, nos.get(r.getOrderId())))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    // ==================== 内部 ====================

    /** 放款：钱包退钱 + 支付记录标退款 + 库存回补 + 订单置终态 + 记金额流水
     *
     * <p>⚠️ 库存回补复用与取消订单**同一条** product.stock 路径 ——
     * 秒杀品另有 flash_sale.soldQuota 路径（见 MEMORY「秒杀销量恒为 0」那条），
     * 这里不动它：售后退的是普通订单行，且会员/积分抵扣不涉及名额。 */
    private void refundNow(OrderEntity order) {
        walletService.refundOrder(order.getUserId(), order.getId(), order.getPayAmount(), "Refund approved");
        paymentRecordRepository.findByOrderId(order.getId()).ifPresent(rec -> {
            // PaymentRecord 没有 refundedAt 字段，状态位才是唯一标记（与 AdminOrderService 同口径）
            rec.setStatus("REFUNDED");
            paymentRecordRepository.save(rec);
        });
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        for (OrderItem it : items) {
            if (it.getProductId() == null) {
                continue;
            }
            productRepository.findById(it.getProductId()).ifPresent(p -> {
                p.setStock(p.getStock() + it.getQuantity());
                productRepository.save(p);
            });
        }
        order.setRefundStatus("APPROVED");
        order.setPaymentStatus("REFUNDED");
        order.setStatus("CLOSED");
        order.setRefundedAt(LocalDateTime.now());
        orderRepository.save(order);
        amountRecordService.recordRefund(order.getUserId(), order.getId(),
                order.getOrderNo(), order.getPayAmount());
    }

    private ReturnRequest ownedBy(Long userId, Long returnId) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该售后单");
        }
        return r;
    }

    private Map<Long, String> orderNosOf(List<ReturnRequest> rows) {
        java.util.Set<Long> ids = rows.stream().map(ReturnRequest::getOrderId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new java.util.HashMap<>();
        for (OrderEntity o : orderRepository.findAllById(ids)) {
            map.put(o.getId(), o.getOrderNo());
        }
        return map;
    }
}
