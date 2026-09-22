package com.example.supermarket.dto;

import com.example.supermarket.entity.MemberDay;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 会员日（后台列表 / 前台展示共用一个响应体）。 */
public class MemberDayResponse {

    private Long id;
    /** 会员日日期（具体年月日，如 2026-10-01） */
    private LocalDate memberDate;
    /** 积分倍率：2 = 双倍 */
    private BigDecimal multiplier;
    private String remark;
    private Integer enabled;
    /** 是否已过期（日期早于今天）—— 后台列表用来标灰，前台不返回 */
    private Boolean expired;

    public static MemberDayResponse from(MemberDay d) {
        MemberDayResponse r = new MemberDayResponse();
        r.setId(d.getId());
        r.setMemberDate(d.getMemberDate());
        r.setMultiplier(d.getMultiplier());
        r.setRemark(d.getRemark());
        r.setEnabled(Integer.valueOf(d.getEnabled()));
        r.setExpired(d.getMemberDate() != null && d.getMemberDate().isBefore(LocalDate.now()));
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getMemberDate() { return memberDate; }
    public void setMemberDate(LocalDate memberDate) { this.memberDate = memberDate; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }

}
