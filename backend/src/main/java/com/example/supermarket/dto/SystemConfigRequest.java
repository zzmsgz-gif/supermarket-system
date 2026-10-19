package com.example.supermarket.dto;

import jakarta.validation.constraints.Size;

/**
 * 后台写入系统配置请求（按 key upsert）。
 */
public class SystemConfigRequest {

    @Size(max = 512, message = "配置值最长 512 字")
    private String value;

    /**
     * 描述可空：不传则不覆盖存量描述（只改值）。
     */
    @Size(max = 255, message = "描述最长 255 字")
    private String description;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
