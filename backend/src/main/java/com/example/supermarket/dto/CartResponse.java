package com.example.supermarket.dto;

import java.math.BigDecimal;
import java.util.List;

public class CartResponse {

    private List<CartItemResponse> items;
    private Integer selectedCount;
    private BigDecimal selectedAmount;
    private BigDecimal activityDiscount;
    private String activityName;

    public CartResponse() {
    }

    public CartResponse(List<CartItemResponse> items, Integer selectedCount, BigDecimal selectedAmount,
            BigDecimal activityDiscount, String activityName) {
        this.items = items;
        this.selectedCount = selectedCount;
        this.selectedAmount = selectedAmount;
        this.activityDiscount = activityDiscount;
        this.activityName = activityName;
    }

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }

    public Integer getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(Integer selectedCount) {
        this.selectedCount = selectedCount;
    }

    public BigDecimal getSelectedAmount() {
        return selectedAmount;
    }

    public void setSelectedAmount(BigDecimal selectedAmount) {
        this.selectedAmount = selectedAmount;
    }

    public BigDecimal getActivityDiscount() {
        return activityDiscount;
    }

    public void setActivityDiscount(BigDecimal activityDiscount) {
        this.activityDiscount = activityDiscount;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

}
