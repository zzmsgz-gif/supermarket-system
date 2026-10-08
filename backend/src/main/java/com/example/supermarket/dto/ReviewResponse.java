package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductReview;
import com.example.supermarket.entity.SysUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class ReviewResponse {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String nickname;
    /**
     * 评价者头像（2026-10-07 用户要求「用户评价时显示头像」）。
     *
     * <p>与 {@code nickname} 的脱敏策略**刻意不同**：昵称打码是为了不泄露真实姓名/手机号，
     * 而头像是用户自己主动上传的公开形象，不属于隐私，电商平台普遍展示。
     *
     * <p>为 null 时前端渲染昵称首字母占位（见 ProductPage 的 review-avatar）。
     */
    private String avatarUrl;
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    /** 商家回复：前台商品详情页要展示出来，否则商家回了也白回（后台的回复动作必须有人看得到） */
    private String replyContent;
    private LocalDateTime replyAt;

    public static ReviewResponse from(ProductReview review, SysUser user, String productName) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setOrderId(review.getOrderId());
        response.setProductId(review.getProductId());
        response.setProductName(productName);
        response.setNickname(maskNickname(user));
        response.setAvatarUrl(user == null ? null : user.getAvatarUrl());
        response.setRating(review.getRating() == null ? null : review.getRating().intValue());
        response.setContent(review.getContent());
        response.setImageUrls(parseImageUrls(review.getImageUrls()));
        response.setCreatedAt(review.getCreatedAt());
        response.setReplyContent(review.getReplyContent());
        response.setReplyAt(review.getReplyAt());
        return response;
    }

    private static List<String> parseImageUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String maskNickname(SysUser user) {
        if (user == null) {
            return "匿名用户";
        }
        String name = user.getNickname();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        if (name == null || name.length() <= 1) {
            return name == null ? "匿名用户" : name + "**";
        }
        return name.charAt(0) + "**";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getReplyContent() {
        return replyContent;
    }

    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }

    public LocalDateTime getReplyAt() {
        return replyAt;
    }

    public void setReplyAt(LocalDateTime replyAt) {
        this.replyAt = replyAt;
    }
}
