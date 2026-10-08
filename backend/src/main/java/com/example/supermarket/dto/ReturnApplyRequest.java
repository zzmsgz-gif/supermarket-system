package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 用户提交售后申请 */
public class ReturnApplyRequest {

    @NotNull(message = "orderId 不能为空")
    private Long orderId;

    @NotBlank(message = "请选择申请原因")
    @Size(max = 60, message = "原因过长")
    private String reason;

    @Size(max = 500, message = "补充说明过长")
    private String remark;

    /** 申请类型：REFUND 只要退款 / RETURN 退货退款 */
    @Size(max = 20)
    private String type;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
