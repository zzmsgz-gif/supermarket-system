package com.example.supermarket.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_sku")
public class ProductSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "spec_json", columnDefinition = "TEXT")
    private String specJson;

    @Column(name = "sku_code", length = 64)
    private String skuCode;

    // 规格价：为空表示该规格跟随商品基准价（兼容只做规格展示、不分价的场景）
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    // 规格吊牌价（划线价）：= 规格价 × (商品原价 / 商品基准价)，随规格变化
    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(length = 500)
    private String image;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(nullable = false)
    private Byte deleted = 0;

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

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
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

    public Byte getDeleted() {
        return deleted;
    }

    public void setDeleted(Byte deleted) {
        this.deleted = deleted;
    }
}
