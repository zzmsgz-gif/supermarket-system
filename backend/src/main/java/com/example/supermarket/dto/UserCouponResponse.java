package com.example.supermarket.dto;

import com.example.supermarket.entity.Coupon;
import com.example.supermarket.entity.UserCoupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserCouponResponse {

    private Long id;
    private Long couponId;
    private String couponName;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Boolean usable;
    private String unusableReason;

    public static UserCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        UserCouponResponse response = new UserCouponResponse();
        response.setId(userCoupon.getId());
        response.setCouponId(coupon.getId());
        response.setCouponName(coupon.getName());
        response.setThresholdAmount(coupon.getThresholdAmount());
        response.setDiscountAmount(coupon.getDiscountAmount());
        response.setStartTime(coupon.getStartTime());
        response.setEndTime(coupon.getEndTime());
        response.setStatus(userCoupon.getStatus());
        LocalDateTime now = LocalDateTime.now();
        boolean expired = coupon.getEndTime() != null && coupon.getEndTime().isBefore(now);
        boolean notStarted = coupon.getStartTime() != null && coupon.getStartTime().isAfter(now);
        boolean usable = "UNUSED".equals(userCoupon.getStatus()) && !expired && !notStarted;
        response.setUsable(usable);
        String reason = null;
        if (!usable) {
            if (!"UNUSED".equals(userCoupon.getStatus())) {
                reason = "USED".equals(userCoupon.getStatus()) ? "已使用" : "已失效";
            } else if (expired) {
                reason = "已过期";
            } else if (notStarted) {
                reason = "未开始";
            }
        }
        response.setUnusableReason(reason);
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public String getCouponName() {
        return couponName;
    }

    public void setCouponName(String couponName) {
        this.couponName = couponName;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getUsable() {
        return usable;
    }

    public void setUsable(Boolean usable) {
        this.usable = usable;
    }

    public String getUnusableReason() {
        return unusableReason;
    }

    public void setUnusableReason(String unusableReason) {
        this.unusableReason = unusableReason;
    }
}
