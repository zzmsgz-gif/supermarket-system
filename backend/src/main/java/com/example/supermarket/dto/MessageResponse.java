package com.example.supermarket.dto;

import com.example.supermarket.entity.UserMessage;
import java.time.LocalDateTime;

public class MessageResponse {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String linkView;
    private String linkRef;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static MessageResponse from(UserMessage message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setType(message.getType());
        response.setTitle(message.getTitle());
        response.setContent(message.getContent());
        response.setLinkView(message.getLinkView());
        response.setLinkRef(message.getLinkRef());
        response.setIsRead(message.getIsRead() != null && message.getIsRead() == UserMessage.READ);
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
