package com.example.supermarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    // 用户名只做登录标识：限定字母/数字/下划线、3-20 位。
    // ⚠️ 不放开中文：中文登录名要打输入法，同一个人下次都可能打不出同一个号（繁简/同音/全半角），
    //    还容易形近仿冒、难搜索难审计。存量账号不受影响 —— 本规则只在注册时校验，登录接口不限制。
    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名只能包含字母、数字、下划线，长度 3-20 位")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password length must be between 6 and 50")
    private String password;

    // 硬上限 12 个字符（视觉宽度 ≤12 列由 AuthService 的 TextWidth 再校验：
    // 6 个汉字 = 12 列算超限，12 个字母 = 12 列刚好）
    @Size(max = 12, message = "昵称过长：中文不超过 6 个字，英文不超过 12 个字母")
    private String nickname;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "Email format is invalid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
