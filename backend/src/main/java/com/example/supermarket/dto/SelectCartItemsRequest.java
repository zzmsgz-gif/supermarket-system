package com.example.supermarket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SelectCartItemsRequest {

    @NotEmpty(message = "Cart item ids are required")
    private List<Long> itemIds;

    @NotNull(message = "Selected is required")
    private Boolean selected;

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

}
