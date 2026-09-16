package com.example.supermarket.dto;

/**
 * 管理员重置密码后的结果。
 *
 * <p>⚠️ tempPassword 只在这一条响应里出现一次，**不落库、不可再查**：
 * 数据库里只写 BCrypt 哈希。管理员必须当场把它转告用户（客服电话/短信），
 * 页面刷新后就再也拿不到了——这正是"管理员也不能事后翻出别人密码"的应有形态。
 */
public class PasswordResetResultResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    private String tempPassword;
    private String message;

    public PasswordResetResultResponse() {
    }

    public PasswordResetResultResponse(Long userId, String username, String nickname, String phone,
                                       String tempPassword, String message) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.phone = phone;
        this.tempPassword = tempPassword;
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getTempPassword() {
        return tempPassword;
    }

    public void setTempPassword(String tempPassword) {
        this.tempPassword = tempPassword;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
