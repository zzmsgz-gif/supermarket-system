package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductAttributeRequest {

    @NotBlank(message = "Attribute name is required")
    @Size(max = 50, message = "Attribute name must be at most 50 characters")
    private String attrName;

    @NotBlank(message = "Attribute value is required")
    @Size(max = 200, message = "Attribute value must be at most 200 characters")
    private String attrValue;

    private Integer sortNo = 0;

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
