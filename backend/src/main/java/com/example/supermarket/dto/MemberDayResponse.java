package com.example.supermarket.dto;

import com.example.supermarket.entity.MemberDay;

/** 会员日（后台列表 / 前台展示共用一个响应体）。 */
public class MemberDayResponse {

    private Long id;
    /** 每月几号（1-31） */
    private Integer dayOfMonth;
    /** 积分倍率：2 = 双倍 */
    private java.math.BigDecimal multiplier;
    private String remark;
    private Integer enabled;

    public static MemberDayResponse from(MemberDay d) {
        MemberDayResponse r = new MemberDayResponse();
        r.setId(d.getId());
        r.setDayOfMonth(d.getDayOfMonth());
        r.setMultiplier(d.getMultiplier());
        r.setRemark(d.getRemark());
        r.setEnabled(Integer.valueOf(d.getEnabled()));
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public java.math.BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(java.math.BigDecimal multiplier) { this.multiplier = multiplier; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

}
