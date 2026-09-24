package com.example.supermarket.dto;

import com.example.supermarket.entity.OrderEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String status;
    private String paymentStatus;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String fulfillmentType;
    private Long pickupStoreId;
    private String pickupStoreName;
    private String pickupCode;
    private String deliverySlot;
    private String remark;
    private String shipCompany;
    private String shipNo;
    private String refundStatus;
    private String refundReason;
    private String refundRemark;
    private LocalDateTime refundedAt;
    private Long userCouponId;
    private String couponName;
    private Long activityId;
    private BigDecimal activityDiscount;
    private String activityName;
    private BigDecimal memberDiscount;
    private BigDecimal pointsDiscount;
    private Long pointsUsed;
    private Long pointsEarned;
    private Integer memberLevel;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    /** 支付截止的**绝对时刻**（服务端按 app.order.pay-timeout-minutes 算出）。
     *  前端倒计时必须用它，不能用「客户端当前时间 + 时长」推算 —— 浏览器与服务端存在时钟偏差，
     *  会算出「还剩 31 分钟」或已过期仍可支付这种荒谬结果。 */
    private LocalDateTime payDeadline;
    private List<OrderItemResponse> items;

    public static OrderResponse from(OrderEntity order, List<OrderItemResponse> items) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setTotalAmount(order.getTotalAmount());
        response.setFreightAmount(order.getFreightAmount());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setPayAmount(order.getPayAmount());
        response.setStatus(order.getStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setReceiverName(order.getReceiverName());
        response.setReceiverPhone(order.getReceiverPhone());
        response.setReceiverAddress(order.getReceiverAddress());
        response.setFulfillmentType(order.getFulfillmentType());
        response.setPickupStoreId(order.getPickupStoreId());
        response.setPickupStoreName(order.getPickupStoreName());
        response.setPickupCode(order.getPickupCode());
        response.setDeliverySlot(order.getDeliverySlot());
        response.setRemark(order.getRemark());
        response.setShipCompany(order.getShipCompany());
        response.setShipNo(order.getShipNo());
        response.setRefundStatus(order.getRefundStatus());
        response.setRefundReason(order.getRefundReason());
        response.setRefundRemark(order.getRefundRemark());
        response.setRefundedAt(order.getRefundedAt());
        response.setUserCouponId(order.getUserCouponId());
        response.setCouponName(order.getCouponName());
        response.setActivityId(order.getActivityId());
        response.setActivityDiscount(order.getActivityDiscount());
        response.setActivityName(order.getActivityName());
        response.setMemberDiscount(order.getMemberDiscount());
        response.setPointsDiscount(order.getPointsDiscount());
        response.setPointsUsed(order.getPointsUsed());
        response.setPointsEarned(order.getPointsEarned());
        response.setMemberLevel(order.getMemberLevel());
        response.setPaidAt(order.getPaidAt());
        response.setShippedAt(order.getShippedAt());
        response.setCompletedAt(order.getCompletedAt());
        response.setCanceledAt(order.getCanceledAt());
        response.setClosedAt(order.getClosedAt());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(items);
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(String fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public Long getPickupStoreId() {
        return pickupStoreId;
    }

    public void setPickupStoreId(Long pickupStoreId) {
        this.pickupStoreId = pickupStoreId;
    }

    public String getPickupStoreName() {
        return pickupStoreName;
    }

    public void setPickupStoreName(String pickupStoreName) {
        this.pickupStoreName = pickupStoreName;
    }

    public String getPickupCode() {
        return pickupCode;
    }

    public void setPickupCode(String pickupCode) {
        this.pickupCode = pickupCode;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getShipCompany() {
        return shipCompany;
    }

    public void setShipCompany(String shipCompany) {
        this.shipCompany = shipCompany;
    }

    public String getShipNo() {
        return shipNo;
    }

    public void setShipNo(String shipNo) {
        this.shipNo = shipNo;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundRemark() {
        return refundRemark;
    }

    public void setRefundRemark(String refundRemark) {
        this.refundRemark = refundRemark;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public String getCouponName() {
        return couponName;
    }

    public void setCouponName(String couponName) {
        this.couponName = couponName;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public BigDecimal getActivityDiscount() {
        return activityDiscount;
    }

    public void setActivityDiscount(BigDecimal activityDiscount) {
        this.activityDiscount = activityDiscount;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public BigDecimal getMemberDiscount() {
        return memberDiscount;
    }

    public void setMemberDiscount(BigDecimal memberDiscount) {
        this.memberDiscount = memberDiscount;
    }

    public BigDecimal getPointsDiscount() {
        return pointsDiscount;
    }

    public void setPointsDiscount(BigDecimal pointsDiscount) {
        this.pointsDiscount = pointsDiscount;
    }

    public Long getPointsUsed() {
        return pointsUsed;
    }

    public void setPointsUsed(Long pointsUsed) {
        this.pointsUsed = pointsUsed;
    }

    public Long getPointsEarned() {
        return pointsEarned;
    }

    public void setPointsEarned(Long pointsEarned) {
        this.pointsEarned = pointsEarned;
    }

    public Integer getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(Integer memberLevel) {
        this.memberLevel = memberLevel;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPayDeadline() {
        return payDeadline;
    }

    public void setPayDeadline(LocalDateTime payDeadline) {
        this.payDeadline = payDeadline;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }
}
