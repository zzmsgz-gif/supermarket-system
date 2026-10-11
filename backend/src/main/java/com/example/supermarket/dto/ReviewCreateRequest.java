package com.example.supermarket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单评价请求。
 *
 * <p>一笔订单可能含多个商品，每个商品需要独立评价（评分/内容/图片互不影响）。
 * 因此请求体从「整单共用一套 rating+content」改为「按订单项逐项提交」。
 */
public class ReviewCreateRequest {

    @Valid
    @NotNull(message = "Review items are required")
    @Size(min = 1, message = "At least one review item is required")
    private List<ReviewItemDTO> reviewItems = new ArrayList<>();

    public List<ReviewItemDTO> getReviewItems() {
        return reviewItems;
    }

    public void setReviewItems(List<ReviewItemDTO> reviewItems) {
        this.reviewItems = reviewItems;
    }

    /** 单个订单项的评价内容（商品粒度） */
    public static class ReviewItemDTO {

        @NotNull(message = "Order item id is required")
        private Long orderItemId;

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        private Integer rating;

        @Size(max = 1000, message = "Content must be at most 1000 characters")
        private String content;

        @Size(max = 9, message = "At most 9 images allowed")
        private List<String> imageUrls;

        public Long getOrderItemId() {
            return orderItemId;
        }

        public void setOrderItemId(Long orderItemId) {
            this.orderItemId = orderItemId;
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
    }
}
