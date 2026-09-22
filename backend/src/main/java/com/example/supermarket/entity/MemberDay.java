package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员日：**指定具体日期**（年月日），当天消费积分翻倍。
 *
 * <p>由后台「会员日」菜单维护，可配多个日期（如 10-01、11-11）。
 * 判定用「下单日」是否命中列表里的某一天，见 {@code MemberDayService.multiplierFor}。
 *
 * <p>⚠️ 原先实现的是「每月几号」（只存 day_of_month、按月循环），2026-09-22 按用户要求改成
 * **具体日期**：管理员从日历上挑哪天就是哪天，不再循环。所以这里存 {@link #memberDate}。
 *
 * <p>⚠️ 公告栏里那条「会员日…」**不再由系统改写** —— 那是运营文案，由管理员自己维护
 * （系统自动生成会与运营的手写内容打架）。
 */
@Entity
@Table(name = "member_day")
public class MemberDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会员日日期（具体到年月日） */
    @Column(name = "member_date", nullable = false)
    private LocalDate memberDate;

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

    public LocalDate getMemberDate() {
        return memberDate;
    }

    public void setMemberDate(LocalDate memberDate) {
        this.memberDate = memberDate;
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
