package com.example.supermarket.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductSkuRequest {

    @NotBlank(message = "Spec JSON is required")
    private String specJson;

    @Size(max = 64, message = "SKU code must be at most 64 characters")
    private String skuCode;

    /** 规格价；为空表示跟随商品基准价。 */
    private BigDecimal price;

    @Size(max = 500, message = "SKU image must be at most 500 characters")
    private String image;

    private Integer sortNo = 0;

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
