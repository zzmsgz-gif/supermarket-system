package com.example.supermarket.dto;

import com.example.supermarket.entity.ReturnRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 售后申请单响应（用户端「我的售后」+ 后台审核用） */
public class ReturnRequestResponse {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String status;
    private String statusLabel;
    private String type;
    private BigDecimal refundAmount;
    private String reason;
    private String remark;
    /** 是否需要寄回 */
    private Boolean needReturn;
    /** 运费责任方：SELLER / BUYER */
    private String freightBorneBy;
    private String freightLabel;
    private String expressCompany;
    private String trackingNo;
    private String adminRemark;
    private LocalDateTime approvedAt;
    private LocalDateTime shippedBackAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    /** 给用户的下一步提示（前端直接显示，避免用户不知道该干什么） */
    private String nextStep;

    public static ReturnRequestResponse from(ReturnRequest r, String orderNo) {
        ReturnRequestResponse resp = new ReturnRequestResponse();
        resp.setId(r.getId());
        resp.setOrderId(r.getOrderId());
        resp.setOrderNo(orderNo);
        resp.setStatus(r.getStatus());
        resp.setStatusLabel(statusLabel(r.getStatus()));
        resp.setType(r.getType());
        resp.setRefundAmount(r.getRefundAmount());
        resp.setReason(r.getReason());
        resp.setRemark(r.getRemark());
        resp.setNeedReturn(r.getNeedReturn());
        resp.setFreightBorneBy(r.getFreightBorneBy());
        resp.setFreightLabel(freightLabel(r.getFreightBorneBy()));
        resp.setExpressCompany(r.getExpressCompany());
        resp.setTrackingNo(r.getTrackingNo());
        resp.setAdminRemark(r.getAdminRemark());
        resp.setApprovedAt(r.getApprovedAt());
        resp.setShippedBackAt(r.getShippedBackAt());
        resp.setCompletedAt(r.getCompletedAt());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setNextStep(nextStep(r));
        return resp;
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "APPLYING" -> "待商家审核";
            case "WAITING_SHIP" -> "待寄回";
            case "SHIPPED_BACK" -> "已寄回，待确认";
            case "APPROVED" -> "退款完成";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }

    private static String freightLabel(String borneBy) {
        if (borneBy == null) {
            return "待商家判定";
        }
        return "SELLER".equals(borneBy) ? "运费商家承担" : "运费用户承担";
    }

    /**
     * 下一步提示 —— 这个字段是本流程的关键。
     * 售后链条长，中间每一步用户都容易卡住不知道该做什么，
     * 把「现在轮到谁做什么」直接算出来给前端显示。
     */
    private static String nextStep(ReturnRequest r) {
        return switch (r.getStatus() == null ? "" : r.getStatus()) {
            case "APPLYING" -> "商家正在审核你的申请，通常 24 小时内处理。";
            case "WAITING_SHIP" -> "商家已同意退款，请把商品寄回并填写快递公司与运单号。";
            case "SHIPPED_BACK" -> "包裹已寄出，商家确认收到后就会退款给你。";
            case "APPROVED" -> "退款已到账，可在金额明细里查看记录。";
            case "REJECTED" -> "申请未通过"
                    + (r.getAdminRemark() == null || r.getAdminRemark().isBlank()
                       ? "，如有疑问请联系客服。" : "：" + r.getAdminRemark());
            default -> "";
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getNeedReturn() {
        return needReturn;
    }

    public void setNeedReturn(Boolean needReturn) {
        this.needReturn = needReturn;
    }

    public String getFreightBorneBy() {
        return freightBorneBy;
    }

    public void setFreightBorneBy(String freightBorneBy) {
        this.freightBorneBy = freightBorneBy;
    }

    public String getFreightLabel() {
        return freightLabel;
    }

    public void setFreightLabel(String freightLabel) {
        this.freightLabel = freightLabel;
    }

    public String getExpressCompany() {
        return expressCompany;
    }

    public void setExpressCompany(String expressCompany) {
        this.expressCompany = expressCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getAdminRemark() {
        return adminRemark;
    }

    public void setAdminRemark(String adminRemark) {
        this.adminRemark = adminRemark;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getShippedBackAt() {
        return shippedBackAt;
    }

    public void setShippedBackAt(LocalDateTime shippedBackAt) {
        this.shippedBackAt = shippedBackAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }
}
