package com.example.supermarket.dto;

/** 后台编辑协议/隐私正文。字段留空表示不改动该项。 */
public class LegalDocRequest {

    private String title;

    private String content;

    private String version;

    /** 1 启用 0 停用 */
    private Integer enabled;

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

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
