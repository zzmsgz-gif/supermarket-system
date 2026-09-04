package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CreateOrderRequest;
import com.example.supermarket.dto.OrderItemResponse;
import com.example.supermarket.dto.OrderResponse;
import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.PaymentRecord;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.StockLog;
import com.example.supermarket.entity.UserAddress;
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
import com.example.supermarket.repository.UserAddressRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
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
    private static final String PAYMENT_CHANNEL_BALANCE = "BALANCE";
    private static final String ORDER_DEDUCT = "ORDER_DEDUCT";
    private static final String CANCEL_RETURN = "CANCEL_RETURN";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
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
            ActivityService activityService
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
    }

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        List<Long> cartItemIds = request.getCartItemIds().stream().distinct().toList();
        UserAddress address = userAddressRepository.findByIdAndUserId(request.getAddressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        List<CartItem> cartItems = cartItemRepository.findByUserIdAndIdInAndSelected(userId, cartItemIds, SELECTED);
        if (cartItems.size() != cartItemIds.size()) {
            throw new ResourceNotFoundException("Selected cart item not found");
        }

        Map<Long, Product> productMap = productRepository.findAllById(
                        cartItems.stream().map(CartItem::getProductId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, HashMap::new));
        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> buildOrderItem(item, requireAvailableProduct(productMap, item.getProductId())))
                .toList();
        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotalAmount)
                .reduce(ZERO, BigDecimal::add);

        OrderEntity order = new OrderEntity();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(ZERO);
        order.setDiscountAmount(ZERO);
        order.setPayAmount(totalAmount);
        order.setStatus(PENDING_PAYMENT);
        order.setPaymentStatus(UNPAID);
        order.setRefundStatus(REFUND_NONE);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(buildAddressSnapshot(address));
        order.setRemark(trimToNull(request.getRemark()));
        OrderEntity savedOrder = orderRepository.save(order);

        BigDecimal couponDiscount = ZERO;
        if (request.getUserCouponId() != null) {
            couponDiscount = couponService.consumeForOrder(userId, request.getUserCouponId(), savedOrder.getId(), totalAmount);
            savedOrder.setUserCouponId(request.getUserCouponId());
        }
        Map<Long, BigDecimal> productSubtotal = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getSubtotalAmount, (left, right) -> left));
        ActivityEvaluation activityEval = activityService.evaluateBestActivity(productSubtotal, productMap);
        BigDecimal activityDiscount = activityEval.getDiscount();
        savedOrder.setDiscountAmount(couponDiscount);
        savedOrder.setActivityId(activityEval.getActivity() != null ? activityEval.getActivity().getId() : null);
        savedOrder.setActivityDiscount(activityDiscount);
        savedOrder.setActivityName(activityEval.getActivity() != null ? activityEval.getActivity().getName() : null);
        savedOrder.setPayAmount(totalAmount.subtract(couponDiscount).subtract(activityDiscount).max(ZERO));
        savedOrder = orderRepository.save(savedOrder);

        List<OrderItem> savedItems = saveOrderItems(savedOrder.getId(), orderItems);
        deductStocks(savedOrder.getId(), userId, savedItems, productMap);
        cartItemRepository.deleteByUserIdAndIds(userId, cartItemIds);
        return OrderResponse.from(savedOrder, toItemResponses(savedItems));
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
        OrderEntity savedOrder = orderRepository.save(order);
        if (paymentRecordRepository.findByOrderId(orderId).isEmpty()) {
            paymentRecordRepository.save(buildPaymentRecord(order, now));
        }
        return OrderResponse.from(savedOrder, toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(savedOrder.getId())));
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!PENDING_PAYMENT.equals(order.getStatus()) && !PAID.equals(order.getStatus())) {
            throw new BusinessException(409, "Only pending payment or paid orders can be canceled");
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
                throw new BusinessException(409, "Insufficient stock");
            }
            stockLogRepository.save(buildStockLog(orderId, userId, product.getId(), item.getQuantity(), stockBefore, stockAfter));
        }
    }

    private OrderItem buildOrderItem(CartItem cartItem, Product product) {
        if (product.getStock() < cartItem.getQuantity()) {
            throw new BusinessException(409, "Insufficient stock");
        }
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setSkuSpec(cartItem.getSkuSpec());
        item.setProductCoverUrl(product.getCoverUrl());
        item.setProductPrice(product.getPrice());
        item.setQuantity(cartItem.getQuantity());
        item.setSubtotalAmount(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        return item;
    }

    private Product requireAvailableProduct(Map<Long, Product> productMap, Long productId) {
        Product product = productMap.get(productId);
        if (product == null || !ON_SALE.equals(product.getStatus()) || product.getDeleted() == null
                || product.getDeleted().byteValue() != NOT_DELETED) {
            throw new ResourceNotFoundException("Product not found");
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
