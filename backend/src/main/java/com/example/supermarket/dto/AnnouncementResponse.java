package com.example.supermarket.dto;

import com.example.supermarket.entity.Announcement;
import java.time.LocalDateTime;

public class AnnouncementResponse {

    private Long id;
    private String title;
    private String content;
    private String type;
    private Integer sortOrder;
    private LocalDateTime publishTime;
    private LocalDateTime createdAt;

    public static AnnouncementResponse from(Announcement a) {
        AnnouncementResponse r = new AnnouncementResponse();
        r.setId(a.getId());
        r.setTitle(a.getTitle());
        r.setContent(a.getContent());
        r.setType(a.getType());
        r.setSortOrder(a.getSortOrder());
        r.setPublishTime(a.getPublishTime());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
