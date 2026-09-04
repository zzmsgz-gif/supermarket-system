package com.example.supermarket.dto;

import com.example.supermarket.entity.Product;

public class ProductDetailResponse extends ProductSummaryResponse {

    private String description;
    private String brand;
    private Byte isHot;
    private Byte isNew;
    private String tags;
    private java.util.List<ProductImageResponse> images = new java.util.ArrayList<>();
    private java.util.List<ProductSkuResponse> skus = new java.util.ArrayList<>();
    private java.util.List<ProductAttributeResponse> attributes = new java.util.ArrayList<>();

    public ProductDetailResponse() {
    }

    public static ProductDetailResponse from(Product product) {
        ProductSummaryResponse summary = ProductSummaryResponse.from(product);
        ProductDetailResponse detail = new ProductDetailResponse();
        detail.setId(summary.getId());
        detail.setCategoryId(summary.getCategoryId());
        detail.setSku(summary.getSku());
        detail.setName(summary.getName());
        detail.setSubtitle(summary.getSubtitle());
        detail.setCoverUrl(summary.getCoverUrl());
        detail.setPrice(summary.getPrice());
        detail.setOriginalPrice(summary.getOriginalPrice());
        detail.setStock(summary.getStock());
        detail.setSales(summary.getSales());
        detail.setUnit(summary.getUnit());
        detail.setStatus(summary.getStatus());
        detail.setBrand(summary.getBrand());
        detail.setIsHot(summary.getIsHot());
        detail.setIsNew(summary.getIsNew());
        detail.setTags(summary.getTags());
        detail.setDescription(product.getDescription());
        return detail;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Byte getIsHot() {
        return isHot;
    }

    public void setIsHot(Byte isHot) {
        this.isHot = isHot;
    }

    public Byte getIsNew() {
        return isNew;
    }

    public void setIsNew(Byte isNew) {
        this.isNew = isNew;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public java.util.List<ProductImageResponse> getImages() {
        return images;
    }

    public void setImages(java.util.List<ProductImageResponse> images) {
        this.images = images;
    }

    public java.util.List<ProductSkuResponse> getSkus() {
        return skus;
    }

    public void setSkus(java.util.List<ProductSkuResponse> skus) {
        this.skus = skus;
    }

    public java.util.List<ProductAttributeResponse> getAttributes() {
        return attributes;
    }

    public void setAttributes(java.util.List<ProductAttributeResponse> attributes) {
        this.attributes = attributes;
    }
}
