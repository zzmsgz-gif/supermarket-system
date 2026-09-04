package com.example.supermarket.dto;

import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.Product;
import java.math.BigDecimal;

public class CartItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private String productCoverUrl;
    private String skuSpec;
    private BigDecimal productPrice;
    private BigDecimal productOriginalPrice;
    private Integer stock;
    private String unit;
    private Integer quantity;
    private Boolean selected;
    private BigDecimal subtotalAmount;

    public CartItemResponse() {
    }

    public static CartItemResponse from(CartItem item, Product product) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setProductSku(product.getSku());
        response.setProductCoverUrl(product.getCoverUrl());
        response.setSkuSpec(item.getSkuSpec());
        response.setProductPrice(product.getPrice());
        response.setProductOriginalPrice(product.getOriginalPrice());
        response.setStock(product.getStock());
        response.setUnit(product.getUnit());
        response.setQuantity(item.getQuantity());
        response.setSelected(Byte.valueOf((byte) 1).equals(item.getSelected()));
        response.setSubtotalAmount(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductCoverUrl() {
        return productCoverUrl;
    }

    public void setProductCoverUrl(String productCoverUrl) {
        this.productCoverUrl = productCoverUrl;
    }

    public String getSkuSpec() {
        return skuSpec;
    }

    public void setSkuSpec(String skuSpec) {
        this.skuSpec = skuSpec;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public BigDecimal getProductOriginalPrice() {
        return productOriginalPrice;
    }

    public void setProductOriginalPrice(BigDecimal productOriginalPrice) {
        this.productOriginalPrice = productOriginalPrice;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

}
