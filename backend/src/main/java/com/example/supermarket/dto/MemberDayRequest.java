package com.example.supermarket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 后台「会员日」新建/编辑请求。 */
public class MemberDayRequest {

    /** 会员日日期（管理员从日历上挑，具体到年月日） */
    @NotNull(message = "请选择一个日期")
    private LocalDate memberDate;

    /** 积分倍率，2 = 双倍。范围校验放在 Service（1.0-10.0），这里只管存在性 */
    private BigDecimal multiplier = new BigDecimal("2.0");

    @Size(max = 60, message = "备注最长 60 字")
    private String remark;

    private Boolean enabled = true;

    public LocalDate getMemberDate() { return memberDate; }
    public void setMemberDate(LocalDate memberDate) { this.memberDate = memberDate; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

}
