package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货/退款申请单（第 13 条：收到货不想要了的完整流程）。
 *
 * <p><b>为什么要单独建表而不是把字段塞进 orders</b>：
 * orders 上原有的 {@code refund_status} 只有「申请/通过/拒绝」三态，
 * 表达不了「已同意但用户还没寄回」这段中间态。硬塞进 orders 会让
 * {@code status}（订单主状态，物流维度）与退款流程状态纠缠在一起 ——
 * 而这两条线本就该独立（本项目已经决定暂不加物流维度）。
 *
 * <p><b>状态机</b>：
 * <pre>
 *   APPLYING ──商家驳回──&gt; REJECTED（终态）
 *      │
 *      ├──商家同意（无需寄回：质量问题/错发/损坏）──&gt; APPROVED ──&gt; 退款完成
 *      │
 *      └──商家同意（需寄回：不想要了）──&gt; WAITING_SHIP ──用户填单号──&gt; SHIPPED_BACK
 *                                                    │
 *                                                    ├──商家确认收货──&gt; APPROVED ──&gt; 退款
 *                                                    └──商家驳回──────&gt; REJECTED（用户可申诉）
 * </pre>
 *
 * <p><b>运费责任</b>（{@link #freightBorneBy}）：
 * <ul>
 *   <li>{@code SELLER} 商家承担 —— 质量问题 / 错发 / 损坏，商家本就该赔</li>
 *   <li>{@code BUYER} 用户承担 —— 七天无理由的「不想要了」</li>
 *   <li>商家判责时选定；{@code BUYER} 时用户寄回，可要求用户上传凭证</li>
 * </ul>
 * 判定责任**发生在商家审核那一步**（管理员在审核弹窗里选），
 * 而不是让用户自己声明 —— 否则用户会全部选「质量问题」骗取运费。
 */
@Entity
@Table(name = "return_request", indexes = {
        @Index(name = "idx_return_order", columnList = "order_id"),
        @Index(name = "idx_return_user", columnList = "user_id,created_at"),
        @Index(name = "idx_return_status", columnList = "status")
})
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 流程状态（不是订单主状态）：
     * APPLYING / WAITING_SHIP / SHIPPED_BACK / APPROVED / REJECTED
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 申请类型：REFUND 只要退款 / RETURN 退货退款（本项目两者资金流相同，字段留着便于以后拆开） */
    @Column(nullable = false, length = 20)
    private String type;

    /** 退款金额（分/元由调用方保证，实体用 BigDecimal） */
    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    /** 用户填的原因 */
    @Column(length = 255)
    private String reason;

    /** 用户补充说明 / 凭证说明 */
    @Column(length = 500)
    private String remark;

    /** 商家是否要求寄回。质量问题等一律 false —— 让用户来回寄是体验灾难 */
    @Column(name = "need_return", nullable = false)
    private Boolean needReturn;

    /** 运费责任方：SELLER / BUYER，商家审核时定 */
    @Column(name = "freight_borne_by", length = 10)
    private String freightBorneBy;

    /** 用户寄回时填的快递公司 */
    @Column(name = "express_company", length = 40)
    private String expressCompany;

    /** 用户寄回时填的运单号 */
    @Column(name = "tracking_no", length = 64)
    private String trackingNo;

    /** 商家审核意见 */
    @Column(name = "admin_remark", length = 500)
    private String adminRemark;

    /** 商家同意（同意寄回）的时间 */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 用户寄回的时间 */
    @Column(name = "shipped_back_at")
    private LocalDateTime shippedBackAt;

    /** 商家确认收到并退款的时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
