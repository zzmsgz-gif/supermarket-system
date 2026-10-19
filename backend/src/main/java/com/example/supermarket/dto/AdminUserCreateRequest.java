package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 后台「新增管理员」请求：只建管理员账号，角色强制 ADMIN、状态启用。 */
public class AdminUserCreateRequest {

    // 与 RegisterRequest 同一套口径：用户名只做登录标识，字母/数字/下划线、3-20 位
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名只能包含字母、数字、下划线，长度 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在 6-50 之间")
    private String password;

    @Size(max = 12, message = "昵称过长：中文不超过 6 个字，英文不超过 12 个字母")
    private String nickname;

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
}
