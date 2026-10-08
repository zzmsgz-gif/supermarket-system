package com.example.supermarket.dto;

import com.example.supermarket.entity.AmountRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 金额流水响应（用户端「金额明细」页用） */
public class AmountRecordResponse {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String type;
    private String typeLabel;
    /** 1=支出，-1=收入。前端按这个决定加号还是减号、以及配色。 */
    private Integer direction;
    private BigDecimal amount;
    private String title;
    private String remark;
    private LocalDateTime createdAt;

    public static AmountRecordResponse from(AmountRecord r, String orderNo) {
        AmountRecordResponse resp = new AmountRecordResponse();
        resp.setId(r.getId());
        resp.setOrderId(r.getOrderId());
        resp.setOrderNo(orderNo);
        resp.setType(r.getType());
        resp.setTypeLabel(label(r.getType()));
        resp.setDirection(r.getDirection());
        resp.setAmount(r.getAmount());
        resp.setTitle(r.getTitle());
        resp.setRemark(r.getRemark());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }

    private static String label(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "ORDER_PAY" -> "订单支付";
            case "ORDER_REFUND" -> "退款";
            case "COUPON_DISCOUNT" -> "优惠券";
            case "ACTIVITY_DISCOUNT" -> "活动优惠";
            case "MEMBER_DISCOUNT" -> "会员优惠";
            case "POINTS_DISCOUNT" -> "积分抵扣";
            case "FREIGHT" -> "运费";
            case "POINTS_EARN" -> "积分";
            default -> type;
        };
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
