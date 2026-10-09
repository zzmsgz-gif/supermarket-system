package com.example.supermarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 个人资料更新（第 9 条）。
 *
 * <p><b>为什么手机号单独一个字段、不跟其它一起无条件改</b>：
 * 手机号是账号的登录凭证与找回通道，**无验证改绑等于任何拿到会话的人都能换绑**。
 * 项目目前没有短信通道（密码重置是管理员生成临时密码），所以这里做成
 * 「必须先通过 {@code /auth/phone-verify} 换取的 verifyToken 才能改」，
 * 通道就绪后只需补一个真正的校验实现，前端不用改。
 */
public class ProfileUpdateRequest {

    @Size(max = 40, message = "昵称过长")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱过长")
    private String email;

    /** 性别：MALE / FEMALE / SECRET，不传表示不改 */
    @Pattern(regexp = "^(MALE|FEMALE|SECRET)$", message = "性别取值不合法")
    private String gender;

    /** 生日 yyyy-MM-dd，不传表示不改；不能填未来日期 */
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "生日格式应为 yyyy-MM-dd")
    private String birthday;

    private String avatarUrl;

    /**
     * 新手机号。
     *
     * <p><b>与旧手机号不同则必须带 verifyToken</b>：服务端会校验该 token
     * 是否为 {@code /auth/phone-verify} 针对「新手机号」签发的一次性凭证。
     * 没有它直接改绑 = 账号可被任意换绑，是安全问题而不是体验问题。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 换绑手机号时的一次性凭证；仅在 phone 与现手机号不同时需要 */
    private String verifyToken;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }
}
