package com.example.supermarket.dto;

/**
 * 商品星级评价聚合：平均分 + 评价数（供商品卡与详情页展示）。
 */
public record RatingSummaryResponse(Long productId, double avgRating, long reviewCount) {
}
