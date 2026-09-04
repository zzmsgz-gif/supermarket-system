package com.example.supermarket.schedule;

import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.service.OrderCloseService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    private final OrderRepository orderRepository;
    private final OrderCloseService orderCloseService;

    @Value("${app.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    public OrderTimeoutScheduler(OrderRepository orderRepository, OrderCloseService orderCloseService) {
        this.orderRepository = orderRepository;
        this.orderCloseService = orderCloseService;
    }

    @Scheduled(fixedDelayString = "${app.order.close-check-interval-ms:60000}", initialDelayString = "10000")
    public void closeTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payTimeoutMinutes);
        List<OrderEntity> timeoutOrders = orderRepository.findByStatusAndCreatedAtBefore(PENDING_PAYMENT, deadline);
        for (OrderEntity order : timeoutOrders) {
            try {
                orderCloseService.closeTimeoutOrder(order);
                log.info("Closed timeout order {} (unpaid over {} minutes)", order.getOrderNo(), payTimeoutMinutes);
            } catch (Exception ex) {
                log.error("Failed to close timeout order {}", order.getOrderNo(), ex);
            }
        }
    }
}
