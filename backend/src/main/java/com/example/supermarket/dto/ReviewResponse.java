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
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private LocalDateTime createdAt;

    public static ReviewResponse from(ProductReview review, SysUser user, String productName) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setOrderId(review.getOrderId());
        response.setProductId(review.getProductId());
        response.setProductName(productName);
        response.setNickname(maskNickname(user));
        response.setRating(review.getRating() == null ? null : review.getRating().intValue());
        response.setContent(review.getContent());
        response.setImageUrls(parseImageUrls(review.getImageUrls()));
        response.setCreatedAt(review.getCreatedAt());
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
}
