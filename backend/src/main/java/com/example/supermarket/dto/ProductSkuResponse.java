package com.example.supermarket.dto;

import java.math.BigDecimal;

import com.example.supermarket.entity.ProductSku;

public class ProductSkuResponse {

    private Long id;
    private String specJson;
    private String skuCode;
    private BigDecimal price;
    private String image;
    private Integer sortNo;

    public static ProductSkuResponse from(ProductSku e) {
        ProductSkuResponse r = new ProductSkuResponse();
        r.setId(e.getId());
        r.setSpecJson(e.getSpecJson());
        r.setSkuCode(e.getSkuCode());
        r.setPrice(e.getPrice());
        r.setImage(e.getImage());
        r.setSortNo(e.getSortNo());
        return r;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
