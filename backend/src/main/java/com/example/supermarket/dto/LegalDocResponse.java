package com.example.supermarket.dto;

import com.example.supermarket.entity.LegalDoc;
import java.time.LocalDateTime;

public class LegalDocResponse {

    private String docKey;
    private String title;
    private String content;
    private String version;
    private Byte enabled;
    private LocalDateTime updatedAt;

    public static LegalDocResponse from(LegalDoc doc) {
        LegalDocResponse response = new LegalDocResponse();
        response.setDocKey(doc.getDocKey());
        response.setTitle(doc.getTitle());
        response.setContent(doc.getContent());
        response.setVersion(doc.getVersion());
        response.setEnabled(doc.getEnabled());
        response.setUpdatedAt(doc.getUpdatedAt());
        return response;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
