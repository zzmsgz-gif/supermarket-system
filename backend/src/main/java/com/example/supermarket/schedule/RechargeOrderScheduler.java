package com.example.supermarket.schedule;

import com.example.supermarket.service.RechargeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RechargeOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RechargeOrderScheduler.class);

    private final RechargeOrderService rechargeOrderService;

    public RechargeOrderScheduler(RechargeOrderService rechargeOrderService) {
        this.rechargeOrderService = rechargeOrderService;
    }

    @Scheduled(fixedDelayString = "${app.recharge.expire-check-interval-ms:60000}", initialDelayString = "20000")
    public void expireOverdueOrders() {
        try {
            rechargeOrderService.expireOverdueOrders();
        } catch (Exception ex) {
            log.error("Failed to expire overdue recharge orders", ex);
        }
    }
}
