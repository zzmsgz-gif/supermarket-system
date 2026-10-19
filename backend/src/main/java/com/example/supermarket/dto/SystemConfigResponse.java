package com.example.supermarket.dto;

import com.example.supermarket.entity.SystemConfig;
import java.time.LocalDateTime;

/**
 * 系统配置对外响应：前台公开读、后台写回都复用它。
 */
public class SystemConfigResponse {

    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;

    public static SystemConfigResponse from(SystemConfig e) {
        SystemConfigResponse r = new SystemConfigResponse();
        r.setConfigKey(e.getConfigKey());
        r.setConfigValue(e.getConfigValue());
        r.setDescription(e.getDescription());
        r.setUpdatedAt(e.getUpdatedAt());
        return r;
    }

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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
