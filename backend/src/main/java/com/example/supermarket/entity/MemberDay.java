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
 * 会员日：**按「每月几号」循环**配置（与公告里「会员日 每月18号」的说法一致）。
 *
 * 会员日当天消费积分翻倍（倍率见 {@link #multiplier}，默认 2 = 双倍）。
 * 可配多个（如 8 / 18 / 28 号），由后台「会员日」菜单维护。
 *
 * ⚠️ 只配到「号」不配到「月」：31 号在 2 月这类小月不存在，那天自然不触发（不做「顺延到月末」，
 * 避免出现"2 月 28 号突然双倍"这种解释不清的行为）。
 */
@Entity
@Table(name = "member_day")
public class MemberDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 每月几号（1-31） */
    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    /** 积分倍率：2 = 双倍（当天消费积分 ×2） */
    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal multiplier = new BigDecimal("2.0");

    /** 展示用备注，如「超级会员日」 */
    @Column(nullable = false, length = 60)
    private String remark = "";

    @Column(nullable = false)
    private Byte enabled = 1;

    // ⚠️ 新增实体这两列必须带 columnDefinition：否则空库首次部署时它们不参与 INSERT，
    //    应用能启动但所有写操作 500（项目里踩过一次，见 MEMORY.md）。
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Byte getEnabled() {
        return enabled;
    }

    public void setEnabled(Byte enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
