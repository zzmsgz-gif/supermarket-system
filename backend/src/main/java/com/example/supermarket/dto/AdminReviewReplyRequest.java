package com.example.supermarket.dto;

import jakarta.validation.constraints.Size;

/** 商家回复评价。传空字符串表示**撤回回复**。 */
public class AdminReviewReplyRequest {

    @Size(max = 500, message = "回复最多 500 字")
    private String replyContent;

    public String getReplyContent() {
        return replyContent;
    }

    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }
}
