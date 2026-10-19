package com.example.supermarket.dto;

import java.math.BigDecimal;
import java.util.List;

public class CartResponse {

    private List<CartItemResponse> items;
    private Integer selectedCount;
    private BigDecimal selectedAmount;
    private BigDecimal activityDiscount;
    private String activityName;
    /** 已勾选商品中「非秒杀部分」的小计。秒杀行不参与活动/券，券门槛与活动门槛据此判定（互斥口径）。 */
    private BigDecimal nonFlashSelectedAmount;

    public CartResponse() {
    }

    public CartResponse(List<CartItemResponse> items, Integer selectedCount, BigDecimal selectedAmount,
            BigDecimal activityDiscount, String activityName, BigDecimal nonFlashSelectedAmount) {
        this.items = items;
        this.selectedCount = selectedCount;
        this.selectedAmount = selectedAmount;
        this.activityDiscount = activityDiscount;
        this.activityName = activityName;
        this.nonFlashSelectedAmount = nonFlashSelectedAmount;
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

    public BigDecimal getNonFlashSelectedAmount() {
        return nonFlashSelectedAmount;
    }

    public void setNonFlashSelectedAmount(BigDecimal nonFlashSelectedAmount) {
        this.nonFlashSelectedAmount = nonFlashSelectedAmount;
    }

}
