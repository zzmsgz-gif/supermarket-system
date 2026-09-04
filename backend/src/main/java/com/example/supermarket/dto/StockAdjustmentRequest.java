package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StockAdjustmentRequest {

    @NotNull(message = "Change quantity is required")
    private Integer changeQuantity;

    @NotBlank(message = "Biz type is required")
    @Size(max = 30, message = "Biz type must be at most 30 characters")
    private String bizType;

    @Size(max = 255, message = "Remark must be at most 255 characters")
    private String remark;

    public Integer getChangeQuantity() {
        return changeQuantity;
    }

    public void setChangeQuantity(Integer changeQuantity) {
        this.changeQuantity = changeQuantity;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
