package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;

public class WechatLoginRequest {

    /** wx.login 拿到的临时登录凭证 code */
    @NotBlank(message = "登录凭证 code 不能为空")
    private String code;

    /** 昵称（可选，用户在小程序内主动填写；首次微信登录后用于展示） */
    private String nickname;

    /** 头像 URL（可选，同上） */
    private String avatarUrl;

    public WechatLoginRequest() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
