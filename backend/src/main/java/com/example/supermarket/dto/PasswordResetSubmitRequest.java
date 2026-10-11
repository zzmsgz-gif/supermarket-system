package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 「忘记密码」申请表单：账号 + 联系电话（供客服核对身份后回拨告知临时密码）。 */
public class PasswordResetSubmitRequest {

    @NotBlank(message = "请输入账号")
    @Size(max = 50, message = "账号过长")
    private String username;

    @NotBlank(message = "请留下联系电话")
    // 正则与前端 utils/validate.js 的 PHONE_RE / RegisterRequest 保持一致：
    // 之前只查 5-50 字符，「123456」也能落库，客服拿到根本打不通。
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
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
