package com.example.supermarket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DwellRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "seconds is required")
    @Positive(message = "seconds must be positive")
    private Integer seconds;

    private String source;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
