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

    /** 领取方式：0=每人限领一次 1=每天可领一次（前端决定按钮文案与可领状态） */
    private Integer claimType;

    /**
     * 「今天是否已领」——只有 claimType=1（每日可领）时才有意义。
     * 前端用它决定按钮是「今日可领」还是「明天再来」，
     * 否则用户点了才知道不能领（体验差）。
     */
    private Boolean claimedToday;

    /**
     * 通用构造（claimedToday 不明时用）。
     *
     * <p>⚠️ claimType 为 null 一律当 0（限领一次）——
     * 存量券在加列之前 created，这一列是 NULL/0，行为必须与从前一致。
     */
    public static CouponResponse from(Coupon coupon, boolean receivedByCurrentUser) {
        return from(coupon, receivedByCurrentUser, false);
    }

    /** 带「今日是否已领」的构造（claimType=1 每日可领时用） */
    public static CouponResponse from(Coupon coupon, boolean receivedByCurrentUser, boolean claimedToday) {
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
        response.setClaimType(coupon.getClaimType() == null ? 0 : coupon.getClaimType().intValue());
        response.setClaimedToday(claimedToday);
        return response;
    }

    public Integer getClaimType() {
        return claimType;
    }

    public void setClaimType(Integer claimType) {
        this.claimType = claimType;
    }

    public Boolean getClaimedToday() {
        return claimedToday;
    }

    public void setClaimedToday(Boolean claimedToday) {
        this.claimedToday = claimedToday;
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
