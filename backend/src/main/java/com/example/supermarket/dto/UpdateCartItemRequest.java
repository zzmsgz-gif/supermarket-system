package com.example.supermarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateCartItemRequest {

    @Min(value = 1, message = "Quantity must be greater than 0")
    @Max(value = 999, message = "Quantity cannot exceed 999")
    private Integer quantity;

    private Boolean selected;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

}
