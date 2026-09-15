package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 协议 / 隐私政策等法律文本，**内容后台可编辑**，避免每次改文案都要发版。
 *
 * <p>{@code docKey} 是稳定的对外标识（TERMS / PRIVACY），前端按 key 拉取；
 * 正文用 TEXT 存（与 {@code Product.description} 同一套做法）。
 *
 * <p>注意：正文里的公司主体、统一社会信用代码、联系方式等是**占位符**，
 * 上线前必须在后台「内容管理 → 协议与隐私」里替换成真实信息。
 */
@Entity
@Table(name = "legal_doc")
public class LegalDoc {

    /** 用户协议 */
    public static final String KEY_TERMS = "TERMS";
    /** 隐私政策 */
    public static final String KEY_PRIVACY = "PRIVACY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_key", nullable = false, length = 40)
    private String docKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 版本号，如 v1.0；变更留痕用 */
    @Column(length = 20)
    private String version;

    @Column(nullable = false)
    private Byte enabled;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

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

    public String getDocKey() {
        return docKey;
    }

    public void setDocKey(String docKey) {
        this.docKey = docKey;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Byte getEnabled() {
        return enabled;
    }

    public void setEnabled(Byte enabled) {
        this.enabled = enabled;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
