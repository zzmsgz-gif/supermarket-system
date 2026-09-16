package com.example.supermarket.dto;

import com.example.supermarket.entity.PasswordResetRequest;
import java.time.LocalDateTime;

/** 后台「找回密码申请」列表项。 */
public class PasswordResetItemResponse {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    private String contact;
    private String status;
    private String remark;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    public static PasswordResetItemResponse from(PasswordResetRequest request, String nickname, String phone) {
        PasswordResetItemResponse response = new PasswordResetItemResponse();
        response.setId(request.getId());
        response.setUserId(request.getUserId());
        response.setUsername(request.getUsername());
        response.setNickname(nickname);
        response.setPhone(phone);
        response.setContact(request.getContact());
        response.setStatus(request.getStatus());
        response.setRemark(request.getRemark());
        response.setHandledAt(request.getHandledAt());
        response.setCreatedAt(request.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
