package com.example.supermarket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public class RechargeOrderCreateRequest {

    @NotNull(message = "充值金额必填")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "金额最多 2 位小数")
    private BigDecimal amount;

    /** ALIPAY / WECHAT */
    @NotBlank(message = "请选择支付方式")
    @Pattern(regexp = "ALIPAY|WECHAT", message = "支付方式仅支持 ALIPAY 或 WECHAT")
    private String method;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
