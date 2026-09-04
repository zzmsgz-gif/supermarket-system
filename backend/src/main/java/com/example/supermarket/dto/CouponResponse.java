package com.example.supermarket.dto;

import com.example.supermarket.entity.Coupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponResponse {

    private Long id;
    private String name;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer totalCount;
    private Integer receivedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Boolean receivedByCurrentUser;

    public static CouponResponse from(Coupon coupon, boolean receivedByCurrentUser) {
        CouponResponse response = new CouponResponse();
        response.setId(coupon.getId());
        response.setName(coupon.getName());
        response.setThresholdAmount(coupon.getThresholdAmount());
        response.setDiscountAmount(coupon.getDiscountAmount());
        response.setTotalCount(coupon.getTotalCount());
        response.setReceivedCount(coupon.getReceivedCount());
        response.setStartTime(coupon.getStartTime());
        response.setEndTime(coupon.getEndTime());
        response.setStatus(coupon.getStatus() == null ? null : coupon.getStatus().intValue());
        response.setReceivedByCurrentUser(receivedByCurrentUser);
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getReceivedCount() {
        return receivedCount;
    }

    public void setReceivedCount(Integer receivedCount) {
        this.receivedCount = receivedCount;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getReceivedByCurrentUser() {
        return receivedByCurrentUser;
    }

    public void setReceivedByCurrentUser(Boolean receivedByCurrentUser) {
        this.receivedByCurrentUser = receivedByCurrentUser;
    }
}
