package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductImage;

public class ProductImageResponse {

    private Long id;
    private String url;
    private Integer sortNo;

    public static ProductImageResponse from(ProductImage e) {
        ProductImageResponse r = new ProductImageResponse();
        r.setId(e.getId());
        r.setUrl(e.getUrl());
        r.setSortNo(e.getSortNo());
        return r;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
