package com.example.supermarket.dto;

import java.math.BigDecimal;

public class DwellRankResponse {

    private Long productId;
    private String productName;
    private BigDecimal avgSeconds;
    private Long viewCount;
    private String coverUrl;

    public DwellRankResponse(Long productId, String productName, BigDecimal avgSeconds, Long viewCount, String coverUrl) {
        this.productId = productId;
        this.productName = productName;
        this.avgSeconds = avgSeconds;
        this.viewCount = viewCount;
        this.coverUrl = coverUrl;
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
}
