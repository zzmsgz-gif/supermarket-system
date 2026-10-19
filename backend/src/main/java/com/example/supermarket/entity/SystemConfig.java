package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 通用 K/V 系统配置表。
 *
 * <p>用途：把原来前端写死的「首页节日氛围」之类开关挪到后台可配。目前唯一在用的 key 是
 * {@code festive_theme}（值：{@code daily} 日常 / {@code midautumn} 中秋 / {@code none} 关闭），
 * 但表本身是通用的，将来别的小开关也能往里塞，不必再建新表。
 *
 * <p>用字符串主键（config_key）而不是自增 id：配置项极少、且语义上「按 key 读写」比「按 id」自然得多。
 */
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 60)
    private String configKey;

    @Column(name = "config_value", length = 512)
    private String configValue;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
