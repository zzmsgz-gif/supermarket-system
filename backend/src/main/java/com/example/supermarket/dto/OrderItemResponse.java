package com.example.supermarket.dto;

import com.example.supermarket.entity.OrderItem;
import java.math.BigDecimal;

public class OrderItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private String productCoverUrl;
    private String skuSpec;
    private BigDecimal productPrice;
    private BigDecimal originalPrice;
    private Long flashSaleId;
    /** 会员让利来源快照：member=商品会员价、tier=等级折扣、null=无让利（含秒杀成交） */
    private String memberSource;
    private Integer quantity;
    private BigDecimal subtotalAmount;
    /** 该订单行是否已退货退款（按商品行维度的售后标记） */
    private Boolean refunded;

    public static OrderItemResponse from(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setProductName(item.getProductName());
        response.setProductSku(item.getProductSku());
        response.setProductCoverUrl(item.getProductCoverUrl());
        response.setSkuSpec(item.getSkuSpec());
        response.setProductPrice(item.getProductPrice());
        response.setOriginalPrice(item.getOriginalPrice());
        response.setFlashSaleId(item.getFlashSaleId());
        response.setMemberSource(item.getMemberSource());
        response.setQuantity(item.getQuantity());
        response.setSubtotalAmount(item.getSubtotalAmount());
        response.setRefunded(item.getRefunded());
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

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public Long getFlashSaleId() {
        return flashSaleId;
    }

    public void setFlashSaleId(Long flashSaleId) {
        this.flashSaleId = flashSaleId;
    }

    public String getMemberSource() {
        return memberSource;
    }

    public void setMemberSource(String memberSource) {
        this.memberSource = memberSource;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public Boolean getRefunded() {
        return refunded;
    }

    public void setRefunded(Boolean refunded) {
        this.refunded = refunded;
    }
}
