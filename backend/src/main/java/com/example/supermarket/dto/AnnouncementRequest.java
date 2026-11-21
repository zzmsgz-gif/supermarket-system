package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 后台公告创建/编辑请求。
 */
public class AnnouncementRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题最长 120 字")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 500, message = "内容最长 500 字")
    private String content;

    /** NOTICE | PROMOTION | ACTIVITY | SERVICE | WARNING（PROMOTION 的标题会出现在首页顶部利益条） */
    private String type = "NOTICE";

    private Integer sortOrder = 0;

    private Boolean enabled = true;

    /** 生效时间：为空表示从发布起一直生效 */
    private LocalDateTime effectiveTime;

    /** 失效时间：为空表示永不自动过期 */
    private LocalDateTime expiryTime;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(LocalDateTime effectiveTime) { this.effectiveTime = effectiveTime; }

    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }
}
