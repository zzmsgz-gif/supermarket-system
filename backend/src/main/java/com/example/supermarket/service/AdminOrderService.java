package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.OrderItemResponse;
import com.example.supermarket.dto.OrderResponse;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.PaymentRecord;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.StockLog;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.PaymentRecordRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.StockLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminOrderService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String SHIPPED = "SHIPPED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELED = "CANCELED";
    private static final String CLOSED = "CLOSED";
    private static final String PAYMENT_REFUNDED = "REFUNDED";
    private static final String PAYMENT_RECORD_REFUNDED = "REFUNDED";
    private static final String CANCEL_RETURN = "CANCEL_RETURN";
    private static final String REFUND_APPLYING = "APPLYING";
    private static final String REFUND_APPROVED = "APPROVED";
    private static final String REFUND_REJECTED = "REJECTED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            PENDING_PAYMENT,
            PAID,
            SHIPPED,
            COMPLETED,
            CANCELED,
            CLOSED
    );
    private static final Set<String> ALLOWED_REFUND_STATUSES = Set.of("NONE", REFUND_APPLYING, REFUND_APPROVED, REFUND_REJECTED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StockLogRepository stockLogRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final WalletService walletService;
    private final CouponService couponService;

    public AdminOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            StockLogRepository stockLogRepository,
            PaymentRecordRepository paymentRecordRepository,
            WalletService walletService,
            CouponService couponService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.stockLogRepository = stockLogRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.walletService = walletService;
        this.couponService = couponService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(int page, int size, String status, Long userId, String orderNo, String refundStatus) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> orders = orderRepository.findAll(buildOrderSpec(status, userId, orderNo, refundStatus), pageable);
        List<OrderResponse> items = orders.getContent().stream()
                .map(order -> OrderResponse.from(order, toItemResponses(order.getId())))
                .toList();
        return PageResponse.of(items, safePage, safeSize, orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        OrderEntity order = getExistingOrder(id);
        return OrderResponse.from(order, toItemResponses(order.getId()));
    }

    @Transactional
    public OrderResponse shipOrder(Long id, String shipCompany, String shipNo) {
        OrderEntity order = getExistingOrder(id);
        if (!PAID.equals(order.getStatus())) {
            throw new BusinessException(409, "Only paid orders can be shipped");
        }
        order.setStatus(SHIPPED);
        order.setShipCompany(shipCompany);
        order.setShipNo(shipNo);
        order.setShippedAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(savedOrder.getId()));
    }

    @Transactional
    public OrderResponse reviewRefund(Long id, Long operatorId, boolean approved, String remark) {
        OrderEntity order = getExistingOrder(id);
        if (!REFUND_APPLYING.equals(order.getRefundStatus())) {
            throw new BusinessException(409, "Order has no pending refund request");
        }
        order.setRefundRemark(remark);
        if (!approved) {
            order.setRefundStatus(REFUND_REJECTED);
            OrderEntity savedOrder = orderRepository.save(order);
            return OrderResponse.from(savedOrder, toItemResponses(savedOrder.getId()));
        }
        walletService.refundOrder(order.getUserId(), order.getId(), order.getPayAmount(), "Refund approved");
        paymentRecordRepository.findByOrderId(order.getId()).ifPresent(this::refundPaymentRecord);
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        returnStocks(order.getId(), operatorId, items);
        order.setRefundStatus(REFUND_APPROVED);
        order.setPaymentStatus(PAYMENT_REFUNDED);
        order.setStatus(CLOSED);
        order.setRefundedAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(items));
    }

    @Transactional
    public OrderResponse completeOrder(Long id) {
        OrderEntity order = getExistingOrder(id);
        if (!SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(409, "Only shipped orders can be completed");
        }
        order.setStatus(COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(savedOrder.getId()));
    }

    @Transactional
    public OrderResponse cancelOrder(Long id, Long operatorId) {
        OrderEntity order = getExistingOrder(id);
        if (!PENDING_PAYMENT.equals(order.getStatus()) && !PAID.equals(order.getStatus())) {
            throw new BusinessException(409, "Only pending payment or paid orders can be canceled");
        }
        if (PAID.equals(order.getStatus())) {
            walletService.refundOrder(order.getUserId(), order.getId(), order.getPayAmount(), "Admin order cancel refund");
            order.setPaymentStatus(PAYMENT_REFUNDED);
            paymentRecordRepository.findByOrderId(order.getId())
                    .ifPresent(record -> refundPaymentRecord(record));
        } else {
            couponService.restoreIfUnpaid(order);
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        returnStocks(order.getId(), operatorId, items);
        order.setStatus(CANCELED);
        order.setCanceledAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder, toItemResponses(items));
    }

    private OrderEntity getExistingOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private void refundPaymentRecord(PaymentRecord record) {
        record.setStatus(PAYMENT_RECORD_REFUNDED);
        paymentRecordRepository.save(record);
    }

    private void returnStocks(Long orderId, Long operatorId, List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int stockBefore = product.getStock();
            int stockAfter = stockBefore + item.getQuantity();
            int updated = productRepository.returnStock(product.getId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException(409, "Failed to return stock");
            }
            stockLogRepository.save(buildReturnStockLog(orderId, operatorId, product.getId(), item.getQuantity(), stockBefore, stockAfter));
        }
    }

    private StockLog buildReturnStockLog(Long orderId, Long operatorId, Long productId, int quantity, int stockBefore, int stockAfter) {
        StockLog log = new StockLog();
        log.setProductId(productId);
        log.setOrderId(orderId);
        log.setChangeQuantity(quantity);
        log.setStockBefore(stockBefore);
        log.setStockAfter(stockAfter);
        log.setBizType(CANCEL_RETURN);
        log.setOperatorId(operatorId);
        log.setRemark("Admin order cancel stock return");
        return log;
    }

    private Specification<OrderEntity> buildOrderSpec(String status, Long userId, String orderNo, String refundStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status)) {
                String normalizedStatus = status.trim();
                if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                    throw new BusinessException(400, "Invalid order status");
                }
                predicates.add(cb.equal(root.get("status"), normalizedStatus));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (StringUtils.hasText(orderNo)) {
                predicates.add(cb.like(root.get("orderNo"), "%" + orderNo.trim() + "%"));
            }
            if (StringUtils.hasText(refundStatus)) {
                String normalizedRefundStatus = refundStatus.trim();
                if (!ALLOWED_REFUND_STATUSES.contains(normalizedRefundStatus)) {
                    throw new BusinessException(400, "Invalid refund status");
                }
                predicates.add(cb.equal(root.get("refundStatus"), normalizedRefundStatus));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<OrderItemResponse> toItemResponses(Long orderId) {
        return toItemResponses(orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    private List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        return items.stream().map(OrderItemResponse::from).toList();
    }
}
