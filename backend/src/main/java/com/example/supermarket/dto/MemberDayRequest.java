package com.example.supermarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 后台「会员日」新建/编辑请求。 */
public class MemberDayRequest {

    /** 每月几号；只配到「号」不到「月」（31 号在小月不存在，那天自然不触发） */
    @NotNull(message = "请选择每月几号")
    @Min(value = 1, message = "日期需在 1-31 之间")
    @Max(value = 31, message = "日期需在 1-31 之间")
    private Integer dayOfMonth;

    /** 积分倍率，2 = 双倍。范围校验放在 Service（1.0-10.0），这里只管存在性 */
    private BigDecimal multiplier = new BigDecimal("2.0");

    @Size(max = 60, message = "备注最长 60 字")
    private String remark;

    private Boolean enabled = true;

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

}
