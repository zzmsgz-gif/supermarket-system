package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 首页头部「热搜」词条。
 *
 * <p>原先这 5 条是前端 App.vue 里写死的字面量：既没有「怎么才算热」的规则，后台也改不了。
 * 改为运营在后台维护（关键词 / 排序 / 启停），前台从 {@code GET /hot-searches} 读取。
 *
 * <p>⚠️ 用软删（{@code deleted}）而不是物理删：种子脚本是按固定 id + {@code INSERT IGNORE} 播默认词的，
 * 若后台物理删掉一条，下次重启种子会把它**重新插回来**（"删了又复活"）。
 * 软删后该行仍在（deleted=1），INSERT IGNORE 会跳过它，删除才算真的生效。
 */
@Entity
@Table(name = "hot_search")
public class HotSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String keyword;

    /** 前台展示文案，可空；空则前台回落成 keyword（展示词与搜索词可以不同，如显示「纯牛奶」搜「牛奶」） */
    @Column(length = 30)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Byte enabled;

    @Column(nullable = false)
    private Byte deleted;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Byte getEnabled() {
        return enabled;
    }

    public void setEnabled(Byte enabled) {
        this.enabled = enabled;
    }

    public Byte getDeleted() {
        return deleted;
    }

    public void setDeleted(Byte deleted) {
        this.deleted = deleted;
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
