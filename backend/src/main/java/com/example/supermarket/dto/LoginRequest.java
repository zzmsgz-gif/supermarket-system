package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /**
     * 登录框里的「记住我」。
     *
     * <p>true → 签发更长有效期的 token（`app.jwt.remember-expiration-seconds`，默认 7 天）；
     * false / null → 用默认有效期（默认 24 小时）。
     *
     * <p>⚠️ 配套的前端行为：勾选时 token 存 localStorage（关浏览器仍免登录），不勾则存 sessionStorage
     * （关浏览器即失效）。两边要一起看才构成完整的「记住我」—— 2026-09-22 之前这个字段根本不存在，
     * 那个复选框是纯装饰、勾不勾都是 24 小时。
     */
    private Boolean remember;

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

    public Boolean getRemember() {
        return remember;
    }

    public void setRemember(Boolean remember) {
        this.remember = remember;
    }
}
