package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 找回密码申请（「忘记密码」走人工审核路线）。
 *
 * <p>为什么不做自助重置：本项目没有邮件 / 短信通道，任何"自己填个手机号就能重置"的实现
 * 都等于把账号送给知道用户名的人。所以这里只受理申请，由管理员在后台核对身份后
 * 生成一次性临时密码，并通过用户预留的手机号告知。
 *
 * <p>防账号枚举：接口对「账号存在」与「账号不存在」返回完全相同的成功文案；
 * 账号不存在时**不落库**（user_id 允许为空是为了将来接入邮件通道后保留未注册申请）。
 */
@Entity
@Table(name = "password_reset_request")
public class PasswordResetRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 命中的账号 id；账号不存在时为 null */
    @Column(name = "user_id")
    private Long userId;

    /** 用户提交的账号（原样保留，便于管理员比对） */
    @Column(nullable = false, length = 50)
    private String username;

    /** 用户填写的联系电话（供客服核对/回拨） */
    @Column(length = 50)
    private String contact;

    @Column(nullable = false, length = 20)
    private String status;

    /** 处理人（管理员 id） */
    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

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

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
