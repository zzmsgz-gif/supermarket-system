package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 「忘记密码」申请表单：账号 + 联系电话（供客服核对身份后回拨告知临时密码）。 */
public class PasswordResetSubmitRequest {

    @NotBlank(message = "请输入账号")
    @Size(max = 50, message = "账号过长")
    private String username;

    @NotBlank(message = "请留下联系电话")
    @Size(min = 5, max = 50, message = "联系电话长度需在 5-50 个字符之间")
    private String contact;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }
}
