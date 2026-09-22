package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CreateOrderRequest;
import com.example.supermarket.dto.OrderItemResponse;
import com.example.supermarket.dto.OrderResponse;
import com.example.supermarket.dto.ReorderResultResponse;
import com.example.supermarket.dto.ReorderSkippedResponse;
import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.FlashSale;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.PaymentRecord;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.StockLog;
import com.example.supermarket.entity.Store;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.UserAddress;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.service.ActivityEvaluation;
import com.example.supermarket.service.ActivityService;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.CartItemRepository;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.PaymentRecordRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.StockLogRepository;
import com.example.supermarket.repository.StoreRepository;
import com.example.supermarket.repository.SysUserRepository;
import com.example.supermarket.repository.UserAddressRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private static final byte SELECTED = 1;
    private static final byte NOT_DELETED = 0;
    private static final String ON_SALE = "ON_SALE";
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String SHIPPED = "SHIPPED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELED = "CANCELED";
    private static final String REFUND_NONE = "NONE";
    private static final String REFUND_APPLYING = "APPLYING";
    private static final String UNPAID = "UNPAID";
    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_REFUNDED = "REFUNDED";
    private static final String PAYMENT_RECORD_REFUNDED = "REFUNDED";

    /** 快递配送运费：商品小计达到门槛免运费，否则收固定运费（页头「满 ¥99 免运费」公告即此规则） */
    private static final BigDecimal EXPRESS_FREE_THRESHOLD = new BigDecimal("99");
    private static final BigDecimal EXPRESS_FREIGHT = new BigDecimal("8");
    private static final String PAYMENT_CHANNEL_BALANCE = "BALANCE";
    private static final String ORDER_DEDUCT = "ORDER_DEDUCT";
    private static final String CANCEL_RETURN = "CANCEL_RETURN";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal POINTS_PER_YUAN = BigDecimal.valueOf(100);
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserAddressRepository userAddressRepository;
    private final StockLogRepository stockLogRepository;
    private final WalletService walletService;
    private final CouponService couponService;
    private final ActivityService activityService;
    private final SysUserRepository sysUserRepository;
    private final MemberService memberService;
    private final StoreRepository storeRepository;
    private final MessageService messageService;
    private final FlashSaleService flashSaleService;
    /** 「再来一单」要逐条走 CartService 的加购校验，避免在这里重写一套库存/限购规则 */
    private final CartService cartService;
    /** 即时配送范围校验：可送达范围 = 所有营业中门店服务区域的并集 */
    private final DeliveryRangeService deliveryRangeService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRecordRepository paymentRecordRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserAddressRepository userAddressRepository,
            StockLogRepository stockLogRepository,
            WalletService walletService,
            CouponService couponService,
            ActivityService activityService,
            SysUserRepository sysUserRepository,
            MemberService memberService,
            StoreRepository storeRepository,
            MessageService messageService,
            FlashSaleService flashSaleService,
            CartService cartService,
            DeliveryRangeService deliveryRangeService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userAddressRepository = userAddressRepository;
        this.stockLogRepository = stockLogRepository;
        this.walletService = walletService;
        this.couponService = couponService;
        this.activityService = activityService;
        this.sysUserRepository = sysUserRepository;
        this.memberService = memberService;
        this.storeRepository = storeRepository;
        this.messageService = messageService;
        this.flashSaleService = flashSaleService;
        this.cartService = cartService;
        this.deliveryRangeService = deliveryRangeService;
    }

    /**
     * 「再来一单」：把历史订单里的商品批量回填购物车。
     *
     * <p>逐条复用 {@link CartService#addItemInternal} 的加购校验 —— 库存、限购、是否在售
     * 全部与正常加购同口径，不在这里另写一套（否则规则迟早会分叉）。
     * 单件失败只记录原因、不影响其余商品，因为历史订单里出现下架/售罄是常态。
     *
     * <p>秒杀价不保留：购物车与下单永远按"此刻进行中"的场次定价，回填历史价没有意义。
     */
    @Transactional
    public ReorderResultResponse reorder(Long userId, Long orderId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        int added = 0;
        List<ReorderSkippedResponse> skipped = new ArrayList<>();
        for (OrderItem item : orderItemRepository.findByOrderIdOrderByIdAsc(orderId)) {
            try {
                cartService.addItemInternal(userId, item.getProductId(), item.getSkuSpec(), item.getQuantity());
                added++;
            } catch (BusinessException | ResourceNotFoundException e) {
                skipped.add(new ReorderSkippedResponse(item.getProductName(), e.getMessage()));
            }
        }
        return new ReorderResultResponse(added, skipped);
    }

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        String fulfillmentType = resolveFulfillmentType(request.getFulfillmentType());
        boolean pickup = OrderEntity.FULFILLMENT_PICKUP.equals(fulfillmentType);

        // 即时配送与快递配送都必须有收货地址；门店自提必须选一家营业中的自提门店 —— 两者互斥，不再无条件强制 addressId
        UserAddress address = null;
        Store store = null;
        if (pickup) {
            if (request.getPickupStoreId() == null) {
                throw new BusinessException(400, "请选择自提门店");
            }
            store = storeRepository.findByIdAndDeleted(request.getPickupStoreId(), NOT_DELETED)
                    .filter(item -> item.getStatus() != null && item.getStatus() == Store.OPEN)
                    .orElseThrow(() -> new BusinessException(400, "该自提门店当前不可用，请重新选择"));
        } else {
            if (request.getAddressId() == null) {
                throw new BusinessException(400, "请选择收货地址");
            }
            address = userAddressRepository.findByIdAndUserId(request.getAddressId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
            // 即时配送是商家自有运力，只能覆盖门店所在区域；快递配送全国可达，不校验。
            // 放在这里（早于购物车任何扣减）是为了让拒绝尽量靠前，别等扣完库存再回滚。
            if (OrderEntity.FULFILLMENT_INSTANT.equals(fulfillmentType)) {
                deliveryRangeService.assertDeliverable(address.getCity(), address.getDistrict());
            }
        }

        List<Long> cartItemIds = request.getCartItemIds().stream().distinct().toList();
        List<CartItem> cartItems = cartItemRepository.findByUserIdAndIdInAndSelected(userId, cartItemIds, SELECTED);
        if (cartItems.size() != cartItemIds.size()) {
            throw new ResourceNotFoundException("Selected cart item not found");
        }

        Map<Long, Product> productMap = productRepository.findAllById(
                        cartItems.stream().map(CartItem::getProductId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, HashMap::new));
        // 限时秒杀：命中「此刻进行中」的场次就参与结算，但按「自动拆分」规则 ——
        // 只有前 min(本行数量, 用户剩余秒杀名额) 件走秒杀价并占名额，超出部分按原价（不占名额）。
        Map<Long, FlashSale> flashSales = flashSaleService.runningByProductIds(
                cartItems.stream().map(CartItem::getProductId).distinct().toList());
        // 每个限购场次在本订单内「还能分给秒杀价的件数」（随逐行拆解递减，跨同商品多规格行共享）
        Map<Long, Integer> flashRemain = new HashMap<>();
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : cartItems) {
            Product product = requireAvailableProduct(productMap, ci.getProductId());
            FlashSale fs = flashSales.get(ci.getProductId());
            BigDecimal regular = product.getPrice();
            if (product.getMemberPrice() != null && product.getMemberPrice().compareTo(regular) < 0) {
                regular = product.getMemberPrice();
            }
            int qty = ci.getQuantity();
            // 整行库存校验必须在拆分之前：拆成「秒杀段 + 原价段」后各自 ≤ 数量，但两段合计不能超过库存
            if (product.getStock() < qty) {
                throw new BusinessException(409, "商品「" + product.getName() + "」库存不足（仅剩 "
                        + product.getStock() + " 件，购物车中有 " + qty + " 件），请调整数量后重试");
            }
            if (fs != null && fs.getFlashPrice().compareTo(regular) < 0) {
                Integer rem = flashRemain.computeIfAbsent(fs.getId(),
                        k -> flashSaleService.remainingForUser(userId, fs));
                int flashQty = (rem == null) ? qty : Math.min(qty, Math.max(rem, 0));
                if (rem != null) {
                    flashRemain.put(fs.getId(), Math.max(rem - flashQty, 0));
                }
                if (flashQty > 0) {
                    orderItems.add(buildOrderItem(ci, product, fs, flashQty));
                }
                int overflow = qty - flashQty;
                if (overflow > 0) {
                    orderItems.add(buildOrderItem(ci, product, null, overflow));
                }
            } else {
                orderItems.add(buildOrderItem(ci, product, null, qty));
            }
        }
        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotalAmount)
                .reduce(ZERO, BigDecimal::add);

        // 运费只由「快递配送」产生，且以商品小计判定免运费门槛
        BigDecimal freight = freightOf(fulfillmentType, totalAmount);

        OrderEntity order = new OrderEntity();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(freight);
        order.setDiscountAmount(ZERO);
        order.setPayAmount(totalAmount);
        order.setStatus(PENDING_PAYMENT);
        order.setPaymentStatus(UNPAID);
        order.setRefundStatus(REFUND_NONE);
        order.setFulfillmentType(fulfillmentType);
        if (pickup) {
            // 自提：收货人＝账号本人（注册已强制手机号）；"地址"存门店快照，让列表/详情统一展示
            SysUser buyerSelf = sysUserRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            order.setReceiverName(displayNameOf(buyerSelf));
            order.setReceiverPhone(buyerSelf.getPhone() == null ? "" : buyerSelf.getPhone());
            order.setReceiverAddress(store.getName() + " · " + store.getAddress());
            order.setPickupStoreId(store.getId());
            order.setPickupStoreName(store.getName());
            order.setPickupCode(pickupCodeOf(order.getOrderNo()));
        } else {
            order.setReceiverName(address.getReceiverName());
            order.setReceiverPhone(address.getReceiverPhone());
            order.setReceiverAddress(buildAddressSnapshot(address));
            // 只有「同城即时配送」有自选的两小时时段；快递时效由第三方决定，传了也不存
            if (OrderEntity.FULFILLMENT_INSTANT.equals(fulfillmentType)) {
                order.setDeliverySlot(trimToNull(request.getDeliverySlot()));
            }
        }
        order.setRemark(trimToNull(request.getRemark()));
        OrderEntity savedOrder = orderRepository.save(order);

        BigDecimal couponDiscount = ZERO;
        if (request.getUserCouponId() != null) {
            CouponService.CouponUsage usage =
                    couponService.consumeForOrder(userId, request.getUserCouponId(), savedOrder.getId(), totalAmount);
            couponDiscount = usage.discountAmount();
            savedOrder.setUserCouponId(request.getUserCouponId());
            savedOrder.setCouponName(usage.couponName());
        }
        // 同一商品可能对应多个订单行（购物车多规格行），活动门槛金额按商品累加所有行
        Map<Long, BigDecimal> productSubtotal = new HashMap<>();
        orderItems.forEach(item ->
                productSubtotal.merge(item.getProductId(), item.getSubtotalAmount(), BigDecimal::add));
        ActivityEvaluation activityEval = activityService.evaluateBestActivity(productSubtotal, productMap);
        BigDecimal activityDiscount = activityEval.getDiscount();
        savedOrder.setDiscountAmount(couponDiscount);
        savedOrder.setActivityId(activityEval.getActivity() != null ? activityEval.getActivity().getId() : null);
        savedOrder.setActivityDiscount(activityDiscount);
        savedOrder.setActivityName(activityEval.getActivity() != null ? activityEval.getActivity().getName() : null);
        savedOrder.setPayAmount(totalAmount.subtract(couponDiscount).subtract(activityDiscount).max(ZERO));
        savedOrder = orderRepository.save(savedOrder);

        // 会员等级折扣 + 积分抵扣（在优惠券/活动优惠之后继续叠加，避免重复计算）
        SysUser buyer = sysUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BigDecimal amountAfterPromo = savedOrder.getPayAmount();
        BigDecimal memberDiscount = memberService.memberDiscount(buyer.getMemberLevel(), amountAfterPromo);
        BigDecimal payBeforePoints = amountAfterPromo.subtract(memberDiscount).max(ZERO);
        BigDecimal maxRedeem = memberService.maxRedeemValue(payBeforePoints);
        Long pointsUsed = 0L;
        if (Boolean.TRUE.equals(request.getUsePoints())) {
            pointsUsed = memberService.reserveRedeem(userId, savedOrder.getId(), request.getPointsToUse(), maxRedeem);
        }
        BigDecimal pointsValue = new BigDecimal(pointsUsed).divide(POINTS_PER_YUAN, 2, RoundingMode.HALF_UP);
        BigDecimal finalPay = payBeforePoints.subtract(pointsValue).max(ZERO);
        // 会员日（每月指定几号）按「下单日」加倍：这里存的就是最终会发放的积分，
        // 支付时 awardOnPaidOrder 用同一个口径（订单创建日）重算，两边不会打架
        long pointsEarned = memberService.earnPoints(finalPay, LocalDate.now());
        savedOrder.setMemberDiscount(memberDiscount);
        // 落库的是"实际抵扣掉的金额"（而非按点数现算），这样订单详情里
        // 小计 - 券 - 活动 - 会员折扣 - 积分抵扣 恒等于实付，不会因任何一处兜底 max(ZERO) 而对不上账
        savedOrder.setPointsDiscount(payBeforePoints.subtract(finalPay));
        savedOrder.setPointsUsed(pointsUsed);
        savedOrder.setPointsEarned(pointsEarned);
        savedOrder.setMemberLevel(buyer.getMemberLevel());
        // 运费不参与任何折扣（券/活动/会员/积分都只作用于商品小计），最后加上去，
        // 保证 total + freight − 券 − activity − member − points = pay 恒成立
        savedOrder.setPayAmount(finalPay.add(freight));
        savedOrder = orderRepository.save(savedOrder);

        List<OrderItem> savedItems = saveOrderItems(savedOrder.getId(), orderItems);
        deductStocks(savedOrder.getId(), userId, savedItems, productMap);
        // 秒杀名额在库存扣减之后占用：名额不足会抛 409，整个下单事务回滚（不会留半张单）
        reserveFlashQuotas(savedOrder.getId(), userId, savedItems, flashSales);
        cartItemRepository.deleteByUserIdAndIds(userId, cartItemIds);
        return OrderResponse.from(savedOrder, toItemResponses(savedItems));
    }

    /** 为命中秒杀的订单行占用名额（每人限购 + 名额原子扣减的校验都在 FlashSaleService 里） */
    private void reserveFlashQuotas(Long orderId, Long userId, List<OrderItem> items,
                                    Map<Long, FlashSale> flashSales) {
        for (OrderItem item : items) {
            if (item.getFlashSaleId() == null) {
                continue;
            }
            // flashSaleId 就是上面 flashSales 里取出来的那一个，直接复用避免多查一次库
            FlashSale sale = flashSales.get(item.getProductId());
            if (sale != null && sale.getId().equals(item.getFlashSaleId())) {
                flashSaleService.reserveQuota(userId, orderId, sale, item.getQuantity());
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(Long userId, int page, int size, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> orders = orderRepository.findAll(buildUserOrderSpec(userId, status), pageable);
        List<OrderResponse> items = orders.getContent().stream()
                .map(order -> OrderResponse.from(order, toItemResponses(
                        orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())
                )))
                .toList();
        return PageResponse.of(items, safePage, safeSize, orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return OrderResponse.from(order, toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())));
    }

    @Transactional
    public OrderResponse payOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BusinessException(409, "Only pending payment orders can be paid");
        }
        walletService.payOrder(userId, order.getId(), order.getPayAmount());
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(PAID);
        order.setPaymentStatus(PAYMENT_PAID);
        order.setPaidAt(now);
        // 会员日积分按**下单日**判定（与下单时存进订单的 pointsEarned 同口径），
        // 所以这里取订单自身的创建日；createdAt 是 DB 生成列，取不到时退回今天
        LocalDate consumeDate = order.getCreatedAt() != null
                ? order.getCreatedAt().toLocalDate() : LocalDate.now();
        memberService.awardOnPaidOrder(userId, orderId, order.getPayAmount(), consumeDate);
        OrderEntity savedOrder = orderRepository.save(order);
        if (paymentRecordRepository.findByOrderId(orderId).isEmpty()) {
            paymentRecordRepository.save(buildPaymentRecord(order, now));
        }
        // 消息中心：支付成功（自提给自提码、即时配送给时段、快递说明交由快递发出）
        messageService.push(userId, UserMessage.TYPE_ORDER, "订单已支付成功",
                paidMessageOf(savedOrder),
                "orderDetail", String.valueOf(savedOrder.getId()), "ORDER:PAID:" + savedOrder.getId());
        return OrderResponse.from(savedOrder, toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(savedOrder.getId())));
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!PENDING_PAYMENT.equals(order.getStatus()) && !PAID.equals(order.getStatus())) {
            throw new BusinessException(409, "Only pending payment or paid orders can be canceled");
        }
        if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
            memberService.refundRedeem(userId, orderId);
        }
        if (PAID.equals(order.getStatus())) {
            walletService.refundOrder(userId, order.getId(), order.getPayAmount(), "Order cancel refund");
            order.setPaymentStatus(PAYMENT_REFUNDED);
            paymentRecordRepository.findByOrderId(order.getId()).ifPresent(this::refundPaymentRecord);
        } else {
            couponService.restoreIfUnpaid(order);
        }
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        returnStocks(order.getId(), userId, orderItems);
        order.setStatus(CANCELED);
        order.setCanceledAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        messageService.push(userId, UserMessage.TYPE_ORDER, "订单已取消",
                "订单 " + savedOrder.getOrderNo() + " 已取消，占用的库存已释放"
                        + (PAYMENT_REFUNDED.equals(savedOrder.getPaymentStatus()) ? "，支付金额已退回钱包余额。" : "。"),
                "orderDetail", String.valueOf(savedOrder.getId()), "ORDER:CANCELED:" + savedOrder.getId());
        return OrderResponse.from(savedOrder, toItemResponses(orderItems));
    }

    @Transactional
    public OrderResponse confirmReceipt(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(409, "Only shipped orders can be confirmed");
        }
        order.setStatus(COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(savedOrder.getId())));
    }

    @Transactional
    public OrderResponse applyRefund(Long userId, Long orderId, String reason) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!PAID.equals(order.getStatus()) && !SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(409, "Only paid or shipped orders can apply for refund");
        }
        if (REFUND_APPLYING.equals(order.getRefundStatus())) {
            throw new BusinessException(409, "Refund request is already under review");
        }
        order.setRefundStatus(REFUND_APPLYING);
        order.setRefundReason(reason);
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(savedOrder.getId())));
    }

    private List<OrderItem> saveOrderItems(Long orderId, List<OrderItem> orderItems) {
        orderItems.forEach(item -> item.setOrderId(orderId));
        return orderItemRepository.saveAll(orderItems);
    }

    private void deductStocks(Long orderId, Long userId, List<OrderItem> orderItems, Map<Long, Product> productMap) {
        for (OrderItem item : orderItems) {
            Product product = requireAvailableProduct(productMap, item.getProductId());
            int stockBefore = product.getStock();
            int stockAfter = stockBefore - item.getQuantity();
            int updated = productRepository.deductStock(product.getId(), item.getQuantity(), ON_SALE, NOT_DELETED);
            if (updated == 0) {
                // 条件 UPDATE 落空有两种可能：库存不足，或期间被后台下架。文案要能覆盖两者并给出商品名。
                throw new BusinessException(409, "商品「" + product.getName() + "」库存不足或已下架（当前仅剩 "
                        + stockBefore + " 件），请回到购物车调整数量后重试");
            }
            stockLogRepository.save(buildStockLog(orderId, userId, product.getId(), item.getQuantity(), stockBefore, stockAfter));
        }
    }

    /**
     * 构造一个订单行。
     *
     * @param quantity 本行件数（秒杀自动拆分时，可能是「秒杀段」或「原价段」的件数，不再等于购物车行数量）
     * @param flashSale 不为 null 且秒杀价更低时，本行按秒杀价并占名额；为 null 则按常规价（不占名额）
     */
    private OrderItem buildOrderItem(CartItem cartItem, Product product, FlashSale flashSale, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setSkuSpec(cartItem.getSkuSpec());
        item.setProductCoverUrl(product.getCoverUrl());
        // 结算单价取「正常售价 / 会员价 / 秒杀价」三者最低：这三者都是"替换单价"型优惠、互不叠加，
        // 取最低对用户最公平，也保证购物车/结算预览/实际下单口径一致。
        BigDecimal unitPrice = product.getPrice();
        if (product.getMemberPrice() != null && product.getMemberPrice().compareTo(unitPrice) < 0) {
            unitPrice = product.getMemberPrice();
        }
        if (flashSale != null && flashSale.getFlashPrice().compareTo(unitPrice) < 0) {
            unitPrice = flashSale.getFlashPrice();
            // 只有真的按秒杀价成交才占名额（会员价更低时走会员价，不消耗秒杀名额）
            item.setFlashSaleId(flashSale.getId());
        }
        item.setProductPrice(unitPrice);
        // 划线价的对照价：命中秒杀时用商品正常售价（秒杀前的价），否则用商品自带的划线价。
        // 仅用于订单详情展示「划线优惠（已省）」，不参与实付扣减。
        item.setOriginalPrice(item.getFlashSaleId() != null ? product.getPrice() : product.getOriginalPrice());
        item.setQuantity(quantity);
        item.setSubtotalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Product requireAvailableProduct(Map<Long, Product> productMap, Long productId) {
        Product product = productMap.get(productId);
        if (product == null) {
            // 文案必须点出是"哪个商品"出了什么问题 —— 笼统的 "Product not found" 让用户
            // 在结算页无从下手（正常路径下前端已提前标出失效行，这里是并发下的兜底）。
            throw new BusinessException(409, "订单中有商品已不存在，请回到购物车移除后重试");
        }
        if (!ON_SALE.equals(product.getStatus()) || product.getDeleted() == null
                || product.getDeleted().byteValue() != NOT_DELETED) {
            throw new BusinessException(409, "商品「" + product.getName() + "」已下架，请回到购物车移除后重试");
        }
        return product;
    }

    private StockLog buildStockLog(Long orderId, Long userId, Long productId, int quantity, int stockBefore, int stockAfter) {
        StockLog log = new StockLog();
        log.setProductId(productId);
        log.setOrderId(orderId);
        log.setChangeQuantity(-quantity);
        log.setStockBefore(stockBefore);
        log.setStockAfter(stockAfter);
        log.setBizType(ORDER_DEDUCT);
        log.setOperatorId(userId);
        log.setRemark("Order stock deduction");
        return log;
    }

    private void returnStocks(Long orderId, Long userId, List<OrderItem> orderItems) {
        // 与库存回滚绑在一起：订单作废时秒杀名额也要还回去，否则名额会被永久占死
        flashSaleService.releaseQuotaForOrder(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int stockBefore = product.getStock();
            int stockAfter = stockBefore + item.getQuantity();
            int updated = productRepository.returnStock(product.getId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException(409, "Failed to return stock");
            }
            stockLogRepository.save(buildReturnStockLog(orderId, userId, product.getId(), item.getQuantity(), stockBefore, stockAfter));
        }
    }

    private StockLog buildReturnStockLog(Long orderId, Long userId, Long productId, int quantity, int stockBefore, int stockAfter) {
        StockLog log = new StockLog();
        log.setProductId(productId);
        log.setOrderId(orderId);
        log.setChangeQuantity(quantity);
        log.setStockBefore(stockBefore);
        log.setStockAfter(stockAfter);
        log.setBizType(CANCEL_RETURN);
        log.setOperatorId(userId);
        log.setRemark("Order cancel stock return");
        return log;
    }

    private PaymentRecord buildPaymentRecord(OrderEntity order, LocalDateTime paidAt) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setPaymentNo(generatePaymentNo());
        record.setChannel(PAYMENT_CHANNEL_BALANCE);
        record.setAmount(order.getPayAmount());
        record.setStatus(PAYMENT_SUCCESS);
        record.setPaidAt(paidAt);
        return record;
    }

    private void refundPaymentRecord(PaymentRecord record) {
        record.setStatus(PAYMENT_RECORD_REFUNDED);
        paymentRecordRepository.save(record);
    }

    private Specification<OrderEntity> buildUserOrderSpec(Long userId, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        return items.stream().map(OrderItemResponse::from).toList();
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String buildAddressSnapshot(UserAddress address) {
        return address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddress();
    }

    /**
     * 履约方式归一化。缺省或未知值按「同城即时配送」处理，保证老客户端兼容；
     * 改造前的旧值 DELIVERY 同样落到 INSTANT（那时还没有即时/快递之分）。
     */
    private String resolveFulfillmentType(String raw) {
        if (raw == null) {
            return OrderEntity.FULFILLMENT_INSTANT;
        }
        String value = raw.trim().toUpperCase();
        if (OrderEntity.FULFILLMENT_PICKUP.equals(value)) {
            return OrderEntity.FULFILLMENT_PICKUP;
        }
        if (OrderEntity.FULFILLMENT_EXPRESS.equals(value)) {
            return OrderEntity.FULFILLMENT_EXPRESS;
        }
        return OrderEntity.FULFILLMENT_INSTANT;
    }

    /** 支付成功后的站内消息：三种履约各自说清楚下一步用户该做什么 */
    private String paidMessageOf(OrderEntity order) {
        if (OrderEntity.FULFILLMENT_PICKUP.equals(order.getFulfillmentType())) {
            return "自提门店：" + order.getPickupStoreName() + "，自提码 " + order.getPickupCode() + "，到店出示即可取货。";
        }
        if (OrderEntity.FULFILLMENT_EXPRESS.equals(order.getFulfillmentType())) {
            boolean charged = order.getFreightAmount() != null && order.getFreightAmount().compareTo(ZERO) > 0;
            return "我们已开始为你备货，将尽快交由快递发出"
                    + (charged ? "（已收运费 " + order.getFreightAmount() + " 元）。" : "（本单已免运费）。");
        }
        return "我们已开始为你备货"
                + (order.getDeliverySlot() != null ? "，配送时段 " + order.getDeliverySlot() : "")
                + "，请留意配送动态。";
    }

    /**
     * 快递配送的运费：以**商品小计**判定门槛（不含运费本身，与券/活动的门槛同口径）。
     * 即时配送（商家自有运力）与门店自提都不收运费。
     */
    private BigDecimal freightOf(String fulfillmentType, BigDecimal goodsAmount) {
        if (!OrderEntity.FULFILLMENT_EXPRESS.equals(fulfillmentType)) {
            return ZERO;
        }
        return goodsAmount.compareTo(EXPRESS_FREE_THRESHOLD) >= 0 ? ZERO : EXPRESS_FREIGHT;
    }

    /** 自提码取订单号后 6 位（订单号里的随机段），到店出示核销 */
    private String pickupCodeOf(String orderNo) {
        if (orderNo == null || orderNo.length() <= 6) {
            return orderNo;
        }
        return orderNo.substring(orderNo.length() - 6);
    }

    private String displayNameOf(SysUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        return user.getUsername();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
