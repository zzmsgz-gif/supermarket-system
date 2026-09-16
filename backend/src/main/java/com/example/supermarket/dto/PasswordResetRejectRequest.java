package com.example.supermarket.dto;

import jakarta.validation.constraints.Size;

/** 驳回找回密码申请时填写的理由（选填，默认「身份核对未通过」）。 */
public class PasswordResetRejectRequest {

    @Size(max = 255, message = "理由过长")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
