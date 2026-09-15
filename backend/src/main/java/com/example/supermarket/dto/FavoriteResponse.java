package com.example.supermarket.dto;

import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.UserFavorite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** 收藏项：附带商品摘要 + 相对「收藏时价格」的降价信息 */
public class FavoriteResponse {

    private Long id;
    private Long productId;
    private BigDecimal priceAtFavorite;
    private BigDecimal currentPrice;
    private BigDecimal dropAmount;
    private Boolean priceDropped;
    private LocalDateTime createdAt;
    private ProductSummaryResponse product;

    public static FavoriteResponse from(UserFavorite favorite, Product product) {
        FavoriteResponse response = new FavoriteResponse();
        response.setId(favorite.getId());
        response.setProductId(favorite.getProductId());
        response.setPriceAtFavorite(favorite.getPriceAtFavorite());
        response.setCreatedAt(favorite.getCreatedAt());
        response.setProduct(ProductSummaryResponse.from(product));

        BigDecimal current = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
        BigDecimal base = favorite.getPriceAtFavorite() == null ? BigDecimal.ZERO : favorite.getPriceAtFavorite();
        BigDecimal drop = base.subtract(current).setScale(2, RoundingMode.HALF_UP);
        response.setCurrentPrice(current);
        response.setDropAmount(drop.max(BigDecimal.ZERO));
        response.setPriceDropped(drop.compareTo(BigDecimal.ZERO) > 0);
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPriceAtFavorite() {
        return priceAtFavorite;
    }

    public void setPriceAtFavorite(BigDecimal priceAtFavorite) {
        this.priceAtFavorite = priceAtFavorite;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getDropAmount() {
        return dropAmount;
    }

    public void setDropAmount(BigDecimal dropAmount) {
        this.dropAmount = dropAmount;
    }

    public Boolean getPriceDropped() {
        return priceDropped;
    }

    public void setPriceDropped(Boolean priceDropped) {
        this.priceDropped = priceDropped;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ProductSummaryResponse getProduct() {
        return product;
    }

    public void setProduct(ProductSummaryResponse product) {
        this.product = product;
    }
}
