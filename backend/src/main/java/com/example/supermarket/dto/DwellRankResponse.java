package com.example.supermarket.dto;

import java.math.BigDecimal;

public class DwellRankResponse {

    private Long productId;
    private String productName;
    private BigDecimal avgSeconds;
    private Long viewCount;
    private String coverUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal memberPrice;
    private Integer stock;
    private String unit;

    public DwellRankResponse(Long productId, String productName, BigDecimal avgSeconds, Long viewCount, String coverUrl,
                             BigDecimal price, BigDecimal originalPrice, BigDecimal memberPrice, Integer stock, String unit) {
        this.productId = productId;
        this.productName = productName;
        this.avgSeconds = avgSeconds;
        this.viewCount = viewCount;
        this.coverUrl = coverUrl;
        this.price = price;
        this.originalPrice = originalPrice;
        this.memberPrice = memberPrice;
        this.stock = stock;
        this.unit = unit;
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

    public BigDecimal getAvgSeconds() {
        return avgSeconds;
    }

    public void setAvgSeconds(BigDecimal avgSeconds) {
        this.avgSeconds = avgSeconds;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getMemberPrice() {
        return memberPrice;
    }

    public void setMemberPrice(BigDecimal memberPrice) {
        this.memberPrice = memberPrice;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
