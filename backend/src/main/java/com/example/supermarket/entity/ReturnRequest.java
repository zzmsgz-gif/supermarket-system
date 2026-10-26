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
 * <p><b>运费</b>：本项目不区分运费承担方（freightBorneBy 已废弃移除），
 * 退货运费统一按平台规则处理，不再由商家在审核时单独判定。
 */
@Entity
@Table(name = "return_request", indexes = {
        @Index(name = "idx_return_order", columnList = "order_id"),
        @Index(name = "idx_return_user", columnList = "user_id,created_at"),
        @Index(name = "idx_return_status", columnList = "status"),
        @Index(name = "idx_return_order_item", columnList = "order_id, order_item_id")
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
     * 按商品行维度的售后：关联的订单行 id。
     * null 表示「整单售后」（存量旧单或按整单申请的单），新申请都带此值 —— 同单不同商品可各自并行售后。
     */
    @Column(name = "order_item_id")
    private Long orderItemId;

    /** 商品行快照：申请时的商品名（避免商品改名 / 下架后售后单上什么都看不出） */
    @Column(name = "item_product_name", length = 120)
    private String itemProductName;

    /** 商品行快照：封面图 */
    @Column(name = "item_cover_url", length = 500)
    private String itemCoverUrl;

    /** 商品行快照：数量 */
    @Column(name = "item_quantity")
    private Integer itemQuantity;

    /** 商品行快照：成交单价 */
    @Column(name = "item_unit_price", precision = 10, scale = 2)
    private BigDecimal itemUnitPrice;

    /** 商品行快照：该行小计（= 退款金额来源） */
    @Column(name = "item_subtotal", precision = 10, scale = 2)
    private BigDecimal itemSubtotal;

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

    /**
     * 本单在 apply 时冻结的发放积分额（方案D-C）。
     * 驳回/取消时按此额释放；退款回收时作为冻结池回收对账基准。
     */
    @Column(name = "frozen_points", nullable = false)
    private Long frozenPoints = 0L;

    /**
     * 方案D-B：退款时已花掉、无法从冻结池回收、被现金退款等价扣回的发放积分额（分）。
     * 申请前已花掉、C 的冻结来不及挡下的那部分 → 这里记其「积分额」，前端按 100积分=¥1 显示「已花积分等价扣回」。
     * 0 表示没有被扣回（申请时积分都还在、或本单无发放积分）。
     */
    @Column(name = "points_deducted", nullable = false)
    private Long pointsDeducted = 0L;

    /** 用户填的原因 */
    @Column(length = 255)
    private String reason;

    /** 用户补充说明 / 凭证说明 */
    @Column(length = 500)
    private String remark;

    /** 商家是否要求寄回。质量问题等一律 false —— 让用户来回寄是体验灾难 */
    @Column(name = "need_return", nullable = false)
    private Boolean needReturn;

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

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public String getItemProductName() {
        return itemProductName;
    }

    public void setItemProductName(String itemProductName) {
        this.itemProductName = itemProductName;
    }

    public String getItemCoverUrl() {
        return itemCoverUrl;
    }

    public void setItemCoverUrl(String itemCoverUrl) {
        this.itemCoverUrl = itemCoverUrl;
    }

    public Integer getItemQuantity() {
        return itemQuantity;
    }

    public void setItemQuantity(Integer itemQuantity) {
        this.itemQuantity = itemQuantity;
    }

    public BigDecimal getItemUnitPrice() {
        return itemUnitPrice;
    }

    public void setItemUnitPrice(BigDecimal itemUnitPrice) {
        this.itemUnitPrice = itemUnitPrice;
    }

    public BigDecimal getItemSubtotal() {
        return itemSubtotal;
    }

    public void setItemSubtotal(BigDecimal itemSubtotal) {
        this.itemSubtotal = itemSubtotal;
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

    public Long getFrozenPoints() {
        return frozenPoints;
    }

    public void setFrozenPoints(Long frozenPoints) {
        this.frozenPoints = frozenPoints;
    }

    public Long getPointsDeducted() {
        return pointsDeducted;
    }

    public void setPointsDeducted(Long pointsDeducted) {
        this.pointsDeducted = pointsDeducted;
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
