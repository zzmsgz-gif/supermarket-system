package com.example.supermarket.dto;

import com.example.supermarket.entity.StockLog;
import java.time.LocalDateTime;

public class StockLogResponse {

    private Long id;
    private Long productId;
    private Long orderId;
    private Integer changeQuantity;
    private Integer stockBefore;
    private Integer stockAfter;
    private String bizType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;

    public static StockLogResponse from(StockLog log) {
        StockLogResponse response = new StockLogResponse();
        response.setId(log.getId());
        response.setProductId(log.getProductId());
        response.setOrderId(log.getOrderId());
        response.setChangeQuantity(log.getChangeQuantity());
        response.setStockBefore(log.getStockBefore());
        response.setStockAfter(log.getStockAfter());
        response.setBizType(log.getBizType());
        response.setOperatorId(log.getOperatorId());
        response.setRemark(log.getRemark());
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getChangeQuantity() {
        return changeQuantity;
    }

    public void setChangeQuantity(Integer changeQuantity) {
        this.changeQuantity = changeQuantity;
    }

    public Integer getStockBefore() {
        return stockBefore;
    }

    public void setStockBefore(Integer stockBefore) {
        this.stockBefore = stockBefore;
    }

    public Integer getStockAfter() {
        return stockAfter;
    }

    public void setStockAfter(Integer stockAfter) {
        this.stockAfter = stockAfter;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
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
