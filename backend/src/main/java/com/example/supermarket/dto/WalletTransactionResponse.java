package com.example.supermarket.dto;

import com.example.supermarket.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletTransactionResponse {

    private Long id;
    private String transactionNo;
    private Long userId;
    private Long orderId;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String status;
    private String remark;
    /** 关联订单号（join 出来的，用户看一串 id 认不出是哪单） */
    private String orderNo;
    /**
     * 方向：1=支出（钱出去），-1=收入（钱进来）。
     *
     * <p><b>由后端根据 type 算好，前端不要自己猜</b> —— 方向判断写死在前端的话，
     * 以后新增一种流水类型就会漏改（2026-10-09「金额明细」按这个字段染色）。
     */
    private Integer direction;
    /** 展示用文案，如「订单支付」「退款到账」「账户充值」 */
    private String typeLabel;
    private LocalDateTime createdAt;

    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return from(transaction, null);
    }

    public static WalletTransactionResponse from(WalletTransaction transaction, String orderNo) {
        WalletTransactionResponse response = new WalletTransactionResponse();
        response.setId(transaction.getId());
        response.setTransactionNo(transaction.getTransactionNo());
        response.setUserId(transaction.getUserId());
        response.setOrderId(transaction.getOrderId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setBalanceBefore(transaction.getBalanceBefore());
        response.setBalanceAfter(transaction.getBalanceAfter());
        response.setStatus(transaction.getStatus());
        response.setRemark(transaction.getRemark());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setOrderNo(orderNo);
        response.setDirection(directionOf(transaction.getType()));
        response.setTypeLabel(labelOf(transaction.getType()));
        return response;
    }

    /**
     * 方向由 type 决定：RECHARGE（充值）/ REFUND（退款）是**钱进来**，
     * PAYMENT（支付）是**钱出去**。未知类型按支出处理并留空文案 ——
     * 宁可显示成支出让人发现，也不要静默不显示。
     */
    private static Integer directionOf(String type) {
        if (type == null) return 1;
        return switch (type) {
            case "RECHARGE", "REFUND" -> -1;
            default -> 1;
        };
    }

    private static String labelOf(String type) {
        if (type == null) return "";
        return switch (type) {
            case "RECHARGE" -> "账户充值";
            case "PAYMENT" -> "订单支付";
            case "REFUND" -> "退款到账";
            default -> type;
        };
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(BigDecimal balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
