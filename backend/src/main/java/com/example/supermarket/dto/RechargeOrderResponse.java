package com.example.supermarket.dto;

import com.example.supermarket.entity.RechargeOrder;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class RechargeOrderResponse {

    private Long id;
    private String orderNo;
    private BigDecimal amount;
    private String method;
    private String status;
    /** 过期时间（epoch 毫秒），前端据此做倒计时 */
    private Long expireAt;
    private Long paidAt;
    private Long createdAt;
    /** 剩余支付秒数（仅 PENDING 有意义） */
    private Long remainingSeconds;

    public static RechargeOrderResponse from(RechargeOrder order) {
        RechargeOrderResponse response = new RechargeOrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setAmount(order.getAmount());
        response.setMethod(order.getMethod());
        response.setStatus(order.getStatus());
        ZoneId zone = ZoneId.systemDefault();
        if (order.getExpireAt() != null) {
            response.setExpireAt(order.getExpireAt().atZone(zone).toInstant().toEpochMilli());
        }
        if (order.getPaidAt() != null) {
            response.setPaidAt(order.getPaidAt().atZone(zone).toInstant().toEpochMilli());
        }
        if (order.getCreatedAt() != null) {
            response.setCreatedAt(order.getCreatedAt().atZone(zone).toInstant().toEpochMilli());
        }
        if (order.getStatus().equals(RechargeOrder.STATUS_PENDING) && order.getExpireAt() != null) {
            long remain = ChronoUnit.SECONDS.between(java.time.LocalDateTime.now(), order.getExpireAt());
            response.setRemainingSeconds(Math.max(remain, 0L));
        } else {
            response.setRemainingSeconds(0L);
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public Long getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Long paidAt) {
        this.paidAt = paidAt;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Long remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }
}
