package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 站内消息（消息中心）。dedupeKey 用于幂等：同一事件重复触发（如订单支付接口被重试）
 * 时靠 UNIQUE(user_id, dedupe_key) 避免重复推送。
 */
@Entity
@Table(name = "user_message")
public class UserMessage {

    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_MEMBER = "MEMBER";
    public static final String TYPE_COUPON = "COUPON";
    public static final String TYPE_SYSTEM = "SYSTEM";

    public static final byte UNREAD = 0;
    public static final byte READ = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String content;

    /** 点击消息跳转的目标视图（orders / orderDetail / coupons / points / shop / favorites） */
    @Column(name = "link_view", length = 40)
    private String linkView;

    /** 跳转参数（订单 id / 商品 id 等） */
    @Column(name = "link_ref", length = 64)
    private String linkRef;

    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private Byte isRead;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLinkView() {
        return linkView;
    }

    public void setLinkView(String linkView) {
        this.linkView = linkView;
    }

    public String getLinkRef() {
        return linkRef;
    }

    public void setLinkRef(String linkRef) {
        this.linkRef = linkRef;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public Byte getIsRead() {
        return isRead;
    }

    public void setIsRead(Byte isRead) {
        this.isRead = isRead;
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
