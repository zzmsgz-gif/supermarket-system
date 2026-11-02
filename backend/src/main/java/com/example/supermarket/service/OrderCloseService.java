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

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CLOSED = "CLOSED";
    private static final String CANCEL_RETURN = "CANCEL_RETURN";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StockLogRepository stockLogRepository;
    private final CouponService couponService;
    private final MessageService messageService;
    private final FlashSaleService flashSaleService;
    /** 超时关单也要还库存，因此同样要失效商品读缓存，见 {@link ProductCacheEvictor} */
    private final ProductCacheEvictor productCacheEvictor;

    public OrderCloseService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            StockLogRepository stockLogRepository,
            CouponService couponService,
            MessageService messageService,
            FlashSaleService flashSaleService,
            ProductCacheEvictor productCacheEvictor
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.stockLogRepository = stockLogRepository;
        this.couponService = couponService;
        this.messageService = messageService;
        this.flashSaleService = flashSaleService;
        this.productCacheEvictor = productCacheEvictor;
    }

    @Transactional
    public void closeTimeoutOrder(OrderEntity order) {
        // 【CAS 闸门】定时任务拿到的是「查询那一刻还在待支付」的订单；从查到关这几秒里，
        // 用户可能刚好点了支付、或自己取消了。必须在动库存之前再抢一次状态：
        // 抢不到就直接返回 —— 否则会出现「钱已经收了、订单却被关掉、库存还了回去」的幽灵库存，
        // 表现就是卖了超卖。这一行是 15 分钟边界上最关键的互斥点。
        if (orderRepository.casStatus(order.getId(), List.of(PENDING_PAYMENT), CLOSED) == 0) {
            return;
        }
        // CAS 带 clearAutomatically，入参 order 已 detached；后续要写字段，重新加载一份
        OrderEntity fresh = orderRepository.findById(order.getId()).orElse(null);
        if (fresh == null) {
            return;
        }
        couponService.restoreIfUnpaid(fresh);
        // 与库存回滚绑在一起：超时关闭也要把秒杀名额还回去
        flashSaleService.releaseQuotaForOrder(fresh.getId());
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(fresh.getId());
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
            logEntry.setOrderId(fresh.getId());
            logEntry.setChangeQuantity(item.getQuantity());
            logEntry.setStockBefore(stockBefore);
            logEntry.setStockAfter(stockBefore + item.getQuantity());
            logEntry.setBizType(CANCEL_RETURN);
            logEntry.setOperatorId(null);
            logEntry.setRemark("Order timeout close stock return");
            stockLogRepository.save(logEntry);
        }
        // 库存还回来了 → 失效商品读缓存，否则用户看到的仍是「已被占用」时的旧库存
        productCacheEvictor.evictProductReads();
        fresh.setClosedAt(LocalDateTime.now());
        orderRepository.save(fresh);
        messageService.push(fresh.getUserId(), UserMessage.TYPE_ORDER, "订单超时已关闭",
                "订单 " + fresh.getOrderNo() + " 超过支付时限已自动关闭，占用的库存与优惠券已释放。",
                "orderDetail", String.valueOf(fresh.getId()), "ORDER:CLOSED:" + fresh.getId());
    }
}
