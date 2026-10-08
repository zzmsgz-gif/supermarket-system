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
 * 金额流水：用户视角的「我的钱都去哪了」。
 *
 * <p><b>为什么不用 wallet_transaction 就够了？</b>因为 wallet_transaction 记的是
 * <b>钱包余额变动</b>，只发生在充值/支付/退款三种场景。而用户真正想看的是
 * 「这笔订单我实付多少、优惠抵了多少、返了多少积分」—— 优惠抵扣**不产生余额变动**，
 * 在 wallet_transaction 里查不到。所以另开一张表，按 <b>订单维度</b>记全部金额构成。
 *
 * <p><b>与 wallet_transaction 的分工</b>：
 * <ul>
 *   <li>{@code wallet_transaction} → 钱包余额怎么变的（对账用，balance_before/after 连续）</li>
 *   <li>{@code amount_record} → 订单金额怎么构成的（展示用，一单多条：实付/券/活动/会员/积分/退款）</li>
 * </ul>
 *
 * <p><b>type 取值</b>（常量在 AmountRecordService，勿散落）：
 * <ul>
 *   <li>{@code ORDER_PAY} 实付支出（负向，amount 记正数靠 direction 区分）</li>
 *   <li>{@code ORDER_REFUND} 退款到账</li>
 *   <li>{@code COUPON_DISCOUNT} / {@code ACTIVITY_DISCOUNT} / {@code MEMBER_DISCOUNT} /
 *       {@code POINTS_DISCOUNT} 各类优惠抵扣</li>
 *   <li>{@code FREIGHT} 运费支出</li>
 *   <li>{@code POINTS_EARN} 积分获得（不涉及金额，amount 记折算金额便于统计）</li>
 * </ul>
 */
@Entity
@Table(name = "amount_record", indexes = {
        @Index(name = "idx_amount_record_user", columnList = "user_id,created_at"),
        @Index(name = "idx_amount_record_order", columnList = "order_id")
})
public class AmountRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 关联订单；积分获得等无订单场景为 null */
    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 24)
    private String type;

    /** 方向：1=支出（付出去），-1=收入（退回来/省下来）。金额恒为正数，方向单独存。 */
    @Column(nullable = false)
    private Integer direction;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 展示用标题，如「订单支付」「满200减50」「积分抵扣」 */
    @Column(nullable = false, length = 40)
    private String title;

    /** 补充说明，如活动名、券名、快递单号 */
    @Column(length = 255)
    private String remark;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
}
