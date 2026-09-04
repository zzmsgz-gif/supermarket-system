package com.example.supermarket.dto;

import com.example.supermarket.entity.Product;

public class StockAlertResponse {

    private Long id;
    private String sku;
    private String name;
    private Integer stock;
    private Integer lowStockThreshold;

    public static StockAlertResponse from(Product product) {
        StockAlertResponse response = new StockAlertResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setStock(product.getStock());
        response.setLowStockThreshold(product.getLowStockThreshold());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
