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
import java.math.BigDecimal;
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

    private static final List<String> OPEN_STATUSES =
            List.of(STATUS_APPLYING, STATUS_WAITING_SHIP, STATUS_SHIPPED_BACK);

    private final ProductRepository productRepository;
    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final WalletService walletService;
    private final MessageService msgService;
    private final FlashSaleService flashSaleService;

    public ReturnRequestService(ProductRepository productRepository,
                                ReturnRequestRepository returnRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                PaymentRecordRepository paymentRecordRepository,
                                WalletService walletService,
                                                MessageService msgService,
                                FlashSaleService flashSaleService) {
        this.productRepository = productRepository;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.walletService = walletService;
        this.msgService = msgService;
        this.flashSaleService = flashSaleService;
    }

    // ==================== 用户端 ====================

    /**
     * 用户提交售后申请。
     *
     * <p>门禁与 OrderService 同步：PAID（已付款未发货）/ SHIPPED（已发货）/ COMPLETED（已收货）均可申请 ——
     * 改前仅 COMPLETED 可申，导致「付完款想退款」和「收到货前发现问题」都没处申诉。
     *
     * <p>多笔售后：每笔订单允许独立发起售后（不同订单的售后互不影响，可并行处理）；
     * 仅当「该订单」已有未终结的售后单时才拦，避免同一订单重复提交在途申请。
     * 被驳回（REJECTED）的订单允许重新申请 —— 驳回不是终态，用户可修正后再次提交。
     */
    @Transactional
    public ReturnRequestResponse apply(Long userId, ReturnApplyRequest req) {
        OrderEntity order = orderRepository.findByIdAndUserId(req.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        // COMPLETED / SHIPPED / PAID 在 OrderService 里是 private，这里用字面量；改动需与 OrderService 同步
        String st = order.getStatus();
        if (!"COMPLETED".equals(st) && !"SHIPPED".equals(st) && !"PAID".equals(st)) {
            throw new BusinessException(409, "当前订单状态不能申请退款/售后。");
        }

        ReturnRequest r = new ReturnRequest();
        r.setUserId(userId);
        r.setOrderId(order.getId());
        r.setStatus(STATUS_APPLYING);
        r.setType(req.getType() == null || req.getType().isBlank() ? "REFUND" : req.getType());
        r.setNeedReturn(false);          // 审核时才定

        Long itemId = req.getOrderItemId();
        if (itemId != null) {
            // ===== 按商品行维度申请：同单不同商品可各自并行售后 =====
            OrderItem target = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                    .filter(it -> itemId.equals(it.getId())).findFirst().orElse(null);
            if (target == null) {
                throw new ResourceNotFoundException("订单中找不到对应的商品行");
            }
            if (Boolean.TRUE.equals(target.getRefunded())) {
                throw new BusinessException(409, "该商品已退货退款，无需重复申请。");
            }
            // 仅拦截「该商品行」的在途售后（APPLYING/WAITING_SHIP/SHIPPED_BACK），不影响其它商品行
            if (returnRepository.findByOrderIdAndOrderItemIdAndStatusIn(order.getId(), itemId, OPEN_STATUSES).isPresent()) {
                throw new BusinessException(409, "该商品已有一笔售后申请正在处理中，请等待商家处理完成。");
            }
            r.setOrderItemId(itemId);
            r.setItemProductName(target.getProductName());
            r.setItemCoverUrl(target.getProductCoverUrl());
            r.setItemQuantity(target.getQuantity());
            r.setItemUnitPrice(target.getProductPrice());
            r.setItemSubtotal(target.getSubtotalAmount());
            r.setRefundAmount(target.getSubtotalAmount());
            r.setReason(req.getReason());
            r.setRemark(req.getRemark());
        } else {
            // ===== 整单售后（存量旧单 / 未传 orderItemId 的兜底） =====
            // 仅拦截「该订单」的在途售后（APPLYING/WAITING_SHIP/SHIPPED_BACK），不同订单互不影响
            if (returnRepository.findByOrderIdAndStatusIn(req.getOrderId(), OPEN_STATUSES).isPresent()) {
                throw new BusinessException(409, "该订单已有一笔售后申请正在处理中，请等待商家处理完成。");
            }
            // 允许重新申请：订单售后状态为 NONE（从未申请）或 REJECTED（上次被驳回）均可再次发起；
            // 终态 APPROVED 与在途态由上面的按单检查拦截。
            String rs = order.getRefundStatus();
            if (rs != null && !"NONE".equals(rs) && !"REJECTED".equals(rs)) {
                throw new BusinessException(409, "该订单已申请过售后，请勿重复提交。");
            }
            r.setRefundAmount(order.getPayAmount());
            r.setReason(req.getReason());
            r.setRemark(req.getRemark());
        }

        ReturnRequest saved = returnRepository.save(r);

        // orders 上的三态同步一份，供订单列表按售后状态筛选。
        // 只从 NONE / REJECTED 升级到 APPLYING，绝不覆盖已有的 PARTIAL / APPLYING（避免按行申请的并发互相降级）。
        if ("NONE".equals(order.getRefundStatus()) || "REJECTED".equals(order.getRefundStatus())) {
            order.setRefundStatus("APPLYING");
            order.setRefundReason(req.getReason());
            orderRepository.save(order);
        }

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

    /** 某订单的全部售后单（订单详情页展示用，按商品行并行后可有多条） */
    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getByOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        List<ReturnRequest> rows = returnRepository.findByOrderId(orderId);
        Map<Long, String> nos = orderNosOf(rows);
        return rows.stream()
                .map(r -> ReturnRequestResponse.from(r, nos.get(r.getOrderId())))
                .toList();
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

        // 自动判定是否需寄回（商家只点「同意/驳回」，无需手动选下拉框）：
        // · 未收到货（已付款未发货 PAID）—— 退款即可，无需寄回；
        // · 已发货 / 已收货（SHIPPED / COMPLETED）—— 退货退款，需用户先寄回。
        boolean needReturn = !"PAID".equals(order.getStatus());
        r.setAdminRemark(req.getRemark());
        r.setApprovedAt(LocalDateTime.now());

        if (!needReturn) {
            // 未收到货：不必寄回，直接放款
            r.setNeedReturn(false);
            r.setFreightBorneBy(FREIGHT_SELLER);
            r.setStatus(STATUS_APPROVED);
            r.setCompletedAt(LocalDateTime.now());
            returnRepository.save(r);
            refundForReturn(order, r);
            msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "退款已到账",
                    "订单 " + order.getOrderNo() + " 退款 " + r.getRefundAmount() + " 元已退回钱包余额。",
                    "orderDetail", String.valueOf(order.getId()), "RETURN:APPROVED:" + r.getId());
            return ReturnRequestResponse.from(r, order.getOrderNo());
        }

        // 需寄回：先不放款，等用户寄 + 商家确认收货。
        // 退货运费默认由用户承担（七天无理由退货运费自理）；商家可在备注里说明愿承担，
        // 或前端仍可选填 freightBorneBy 覆盖（SELLER / BUYER 二选一）。
        String borne = (req.getFreightBorneBy() != null && !req.getFreightBorneBy().isBlank())
                ? req.getFreightBorneBy() : FREIGHT_BUYER;
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
        refundForReturn(order, r);

        msgService.push(order.getUserId(), UserMessage.TYPE_ORDER, "退款已到账",
                "订单 " + order.getOrderNo() + " 退货已确认，退款 " + r.getRefundAmount() + " 元已退回钱包余额。",
                "orderDetail", String.valueOf(order.getId()), "RETURN:COMPLETED:" + r.getId());

        return ReturnRequestResponse.from(r, order.getOrderNo());
    }

    /** 后台列表（按状态筛，空=全部；逗号分隔多状态=汇总，如「进行中」= APPLYING,WAITING_SHIP,SHIPPED_BACK） */
    @Transactional(readOnly = true)
    public PageResponse<ReturnRequestResponse> listAll(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        List<ReturnRequest> rows;
        long total;
        if (status == null || status.isBlank()) {
            rows = returnRepository.findAllPaged(pageable);
            total = returnRepository.count();
        } else if (status.indexOf(',') >= 0) {
            // 多状态汇总：拆逗号后走 findByStatusIn / countByStatusIn（「进行中」汇总用）
            List<String> statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            rows = returnRepository.findByStatusIn(statuses, pageable);
            total = returnRepository.countByStatusIn(statuses);
        } else {
            rows = returnRepository.findByStatus(status, pageable);
            total = returnRepository.countByStatus(status);
        }
        Map<Long, String> nos = orderNosOf(rows);
        List<ReturnRequestResponse> items = rows.stream()
                .map(r -> ReturnRequestResponse.from(r, nos.get(r.getOrderId())))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    // ==================== 内部 ====================

    /**
     * 放款：按售后单是否按行，决定「整单退」还是「按商品行退」。
     * 按行售后（orderItemId 非空）→ 只退这一行的钱/库存/名额，标记该行已退，订单可能仍部分在途；
     * 整单售后（orderItemId 空，存量旧单）→ 退整单，订单置终态。
     *
     * <p>⚠️ 回补口径必须与下单**完全对称**，否则名额/库存会泄漏：
     * 下单时（OrderService.createOrder）对命中秒杀的订单行既扣 product.stock（deductStocks）
     * 又占 flash_sale.soldQuota（reserveFlashQuotas）。退款作为订单的「另一种关闭」，
     * 必须两条都还 —— product.stock 在下面循环里回补，soldQuota 调 releaseQuotaForOrder /
     * releaseQuotaForOrderItem 回退（与取消/超时关闭同一条路径）。漏掉 soldQuota 会让用户名额和
     * 会场名额被永久占用，导致其它买家被误拦、且会场显示「已抢光」却实际有退款空位。
     */
    private void refundForReturn(OrderEntity order, ReturnRequest r) {
        if (r.getOrderItemId() != null) {
            OrderItem item = orderItemRepository.findById(r.getOrderItemId()).orElse(null);
            refundItem(order, item);
        } else {
            refundWholeOrder(order);
        }
    }

    /** 整单退款（兼容存量整单售后单：orderItemId 为空时走这条） */
    private void refundWholeOrder(OrderEntity order) {
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
        // 秒杀品退款：把下单时占用的名额还回去（含独立秒杀商品与基于原商品的秒杀）。
        // 无秒杀行的普通订单进来也是 no-op，安全。
        flashSaleService.releaseQuotaForOrder(order.getId());
        order.setRefundStatus("APPROVED");
        order.setPaymentStatus("REFUNDED");
        order.setStatus("CLOSED");
        order.setRefundedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    /**
     * 按商品行退款：只退这一行的金额、回补这一行的库存、释放这一行的秒杀名额、标记该行已退。
     * 订单仅在「全部商品行都退完」时才置终态 CLOSED + refundStatus=APPROVED；
     * 否则仅保持 / 升级订单的售后态（APPLYING 在途 / PARTIAL 已退部分且无在途），订单本身不关闭。
     */
    private void refundItem(OrderEntity order, OrderItem item) {
        if (item == null) {
            return;
        }
        BigDecimal amount = item.getSubtotalAmount();
        walletService.refundOrder(order.getUserId(), order.getId(), amount, "Refund approved (item)");
        paymentRecordRepository.findByOrderId(order.getId()).ifPresent(rec -> {
            rec.setStatus("REFUNDED");
            paymentRecordRepository.save(rec);
        });
        if (item.getProductId() != null) {
            productRepository.findById(item.getProductId()).ifPresent(p -> {
                p.setStock(p.getStock() + item.getQuantity());
                productRepository.save(p);
            });
        }
        flashSaleService.releaseQuotaForOrderItem(order.getId(), item.getId());
        item.setRefunded(true);
        orderItemRepository.save(item);
        recomputeOrderRefundStatus(order);
    }

    /**
     * 重算订单的售后态（按商品行维度）：
     *   · 全部商品行都已退 → APPROVED + 订单 CLOSED（全额退款终态）
     *   · 还有在途售后 / 已退部分 → APPLYING（在途）/ PARTIAL（已退部分且无在途）
     *   · 都没有 → NONE
     */
    private void recomputeOrderRefundStatus(OrderEntity order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        boolean allRefunded = !items.isEmpty()
                && items.stream().allMatch(it -> Boolean.TRUE.equals(it.getRefunded()));
        boolean anyRefunded = items.stream().anyMatch(it -> Boolean.TRUE.equals(it.getRefunded()));
        boolean hasOpen = returnRepository.findByOrderIdAndStatusIn(order.getId(), OPEN_STATUSES).isPresent();
        if (allRefunded) {
            order.setRefundStatus("APPROVED");
            order.setPaymentStatus("REFUNDED");
            order.setStatus("CLOSED");
            order.setRefundedAt(LocalDateTime.now());
        } else if (hasOpen || anyRefunded) {
            order.setRefundStatus(hasOpen ? "APPLYING" : "PARTIAL");
        } else {
            order.setRefundStatus("NONE");
        }
        orderRepository.save(order);
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
