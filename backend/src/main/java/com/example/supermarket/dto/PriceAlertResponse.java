package com.example.supermarket.dto;

import com.example.supermarket.entity.PriceAlert;
import com.example.supermarket.entity.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** 降价提醒：附带商品摘要，方便前端直接渲染成商品卡 + 降幅角标 */
public class PriceAlertResponse {

    private Long id;
    private Long productId;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal dropAmount;
    private Integer dropPercent;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private ProductSummaryResponse product;

    public static PriceAlertResponse from(PriceAlert alert, Product product) {
        PriceAlertResponse response = new PriceAlertResponse();
        response.setId(alert.getId());
        response.setProductId(alert.getProductId());
        response.setOldPrice(alert.getOldPrice());
        response.setNewPrice(alert.getNewPrice());
        response.setDropAmount(alert.getDropAmount());
        response.setIsRead(alert.getIsRead() != null && alert.getIsRead() == PriceAlert.READ);
        response.setCreatedAt(alert.getCreatedAt());
        BigDecimal old = alert.getOldPrice() == null ? BigDecimal.ZERO : alert.getOldPrice();
        BigDecimal drop = alert.getDropAmount() == null ? BigDecimal.ZERO : alert.getDropAmount();
        response.setDropPercent(old.compareTo(BigDecimal.ZERO) > 0
                ? drop.multiply(BigDecimal.valueOf(100)).divide(old, 0, RoundingMode.HALF_UP).intValue()
                : 0);
        response.setProduct(product == null ? null : ProductSummaryResponse.from(product));
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

    public BigDecimal getOldPrice() {
        return oldPrice;
    }

    public void setOldPrice(BigDecimal oldPrice) {
        this.oldPrice = oldPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(BigDecimal newPrice) {
        this.newPrice = newPrice;
    }

    public BigDecimal getDropAmount() {
        return dropAmount;
    }

    public void setDropAmount(BigDecimal dropAmount) {
        this.dropAmount = dropAmount;
    }

    public Integer getDropPercent() {
        return dropPercent;
    }

    public void setDropPercent(Integer dropPercent) {
        this.dropPercent = dropPercent;
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

    public ProductSummaryResponse getProduct() {
        return product;
    }

    public void setProduct(ProductSummaryResponse product) {
        this.product = product;
    }
}
