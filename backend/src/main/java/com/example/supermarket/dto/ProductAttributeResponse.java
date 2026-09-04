package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductAttribute;

public class ProductAttributeResponse {

    private Long id;
    private String attrName;
    private String attrValue;
    private Integer sortNo;

    public static ProductAttributeResponse from(ProductAttribute e) {
        ProductAttributeResponse r = new ProductAttributeResponse();
        r.setId(e.getId());
        r.setAttrName(e.getAttrName());
        r.setAttrValue(e.getAttrValue());
        r.setSortNo(e.getSortNo());
        return r;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAttrName() {
        return attrName;
    }

    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }

    public String getAttrValue() {
        return attrValue;
    }

    public void setAttrValue(String attrValue) {
        this.attrValue = attrValue;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
