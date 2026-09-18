package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductReview;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 后台「评价管理」列表项。
 *
 * <p>与前台 {@link ReviewResponse} 的关键差别：**这里用真实昵称/账号，不做打码** ——
 * 商家要能定位到具体是哪位顾客（联系、补偿、判断是否刷评），打码会让这个页面失去意义。
 */
public class AdminReviewResponse {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Long userId;
    private String username;
    private String nickname;
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private String replyContent;
    private LocalDateTime replyAt;
    /** 是否已隐藏（前台不展示，但订单仍算已评价） */
    private Boolean hidden;

    public static AdminReviewResponse from(ProductReview review, String productName, String username, String nickname) {
        AdminReviewResponse response = new AdminReviewResponse();
        response.setId(review.getId());
        response.setOrderId(review.getOrderId());
        response.setProductId(review.getProductId());
        response.setProductName(productName);
        response.setUserId(review.getUserId());
        response.setUsername(username);
        response.setNickname(nickname);
        response.setRating(review.getRating() == null ? null : review.getRating().intValue());
        response.setContent(review.getContent());
        response.setImageUrls(parseImageUrls(review.getImageUrls()));
        response.setCreatedAt(review.getCreatedAt());
        response.setReplyContent(review.getReplyContent());
        response.setReplyAt(review.getReplyAt());
        response.setHidden(review.getHidden() != null && review.getHidden() != 0);
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }
}
