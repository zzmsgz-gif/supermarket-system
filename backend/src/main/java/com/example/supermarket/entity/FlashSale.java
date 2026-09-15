package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限时秒杀（一个商品一个场次）。
 *
 * <p>与 {@link Activity}（满减/折扣，按订单或商品小计算）不同，秒杀是**单品定价**：
 * 命中后直接用 {@code flashPrice} 替换订单行单价，因此不需要在订单上加单独的优惠金额列
 * —— 优惠已经体现在 {@code order_item.product_price} 里，与「会员价」同一套口径。
 *
 * <p>名额（{@code totalQuota}/{@code soldQuota}）是独立于商品库存的**秒杀名额**，
 * 用带条件的 UPDATE 原子扣减防超卖，取消/超时/退款时按 {@code order_item.flash_sale_id} 回退。
 */
@Entity
@Table(name = "flash_sale")
public class FlashSale {

    public static final byte ENABLED = 1;
    public static final byte DISABLED = 0;
    public static final byte NOT_DELETED = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 场次名，如「早市秒杀」 */
    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "flash_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal flashPrice;

    /** 秒杀总名额（与商品库存相互独立：名额抢完即结束，即使商品还有库存） */
    @Column(name = "total_quota", nullable = false)
    private Integer totalQuota;

    @Column(name = "sold_quota", nullable = false)
    private Integer soldQuota = 0;

    /** 每人限购件数，0 表示不限购 */
    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 0;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** 1 启用 0 停用（停用后前台不展示、不参与计价） */
    @Column(nullable = false)
    private Byte status;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(nullable = false)
    private Byte deleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    /** 名额是否已抢完 */
    public boolean soldOut() {
        return soldQuota != null && totalQuota != null && soldQuota >= totalQuota;
    }

    /** 在给定时刻是否进行中 */
    public boolean activeAt(LocalDateTime at) {
        return ENABLED == (status == null ? DISABLED : status)
                && NOT_DELETED == (deleted == null ? (byte) 1 : deleted)
                && startTime != null && endTime != null
                && !at.isBefore(startTime) && at.isBefore(endTime)
                && !soldOut();
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

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getFlashPrice() {
        return flashPrice;
    }

    public void setFlashPrice(BigDecimal flashPrice) {
        this.flashPrice = flashPrice;
    }

    public Integer getTotalQuota() {
        return totalQuota;
    }

    public void setTotalQuota(Integer totalQuota) {
        this.totalQuota = totalQuota;
    }

    public Integer getSoldQuota() {
        return soldQuota;
    }

    public void setSoldQuota(Integer soldQuota) {
        this.soldQuota = soldQuota;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
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

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Byte getDeleted() {
        return deleted;
    }

    public void setDeleted(Byte deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
