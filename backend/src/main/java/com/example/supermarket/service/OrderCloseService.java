package com.example.supermarket.service;

import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.StockLog;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.StockLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCloseService {

    private static final String CLOSED = "CLOSED";
    private static final String CANCEL_RETURN = "CANCEL_RETURN";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StockLogRepository stockLogRepository;
    private final CouponService couponService;
    private final MessageService messageService;
    private final FlashSaleService flashSaleService;

    public OrderCloseService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            StockLogRepository stockLogRepository,
            CouponService couponService,
            MessageService messageService,
            FlashSaleService flashSaleService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.stockLogRepository = stockLogRepository;
        this.couponService = couponService;
        this.messageService = messageService;
        this.flashSaleService = flashSaleService;
    }

    @Transactional
    public void closeTimeoutOrder(OrderEntity order) {
        couponService.restoreIfUnpaid(order);
        // 与库存回滚绑在一起：超时关闭也要把秒杀名额还回去
        flashSaleService.releaseQuotaForOrder(order.getId());
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                continue;
            }
            int stockBefore = product.getStock();
            int updated = productRepository.returnStock(product.getId(), item.getQuantity());
            if (updated == 0) {
                continue;
            }
            StockLog logEntry = new StockLog();
            logEntry.setProductId(product.getId());
            logEntry.setOrderId(order.getId());
            logEntry.setChangeQuantity(item.getQuantity());
            logEntry.setStockBefore(stockBefore);
            logEntry.setStockAfter(stockBefore + item.getQuantity());
            logEntry.setBizType(CANCEL_RETURN);
            logEntry.setOperatorId(null);
            logEntry.setRemark("Order timeout close stock return");
            stockLogRepository.save(logEntry);
        }
        order.setStatus(CLOSED);
        order.setClosedAt(LocalDateTime.now());
        orderRepository.save(order);
        messageService.push(order.getUserId(), UserMessage.TYPE_ORDER, "订单超时已关闭",
                "订单 " + order.getOrderNo() + " 超过支付时限已自动关闭，占用的库存与优惠券已释放。",
                "orderDetail", String.valueOf(order.getId()), "ORDER:CLOSED:" + order.getId());
    }
}
