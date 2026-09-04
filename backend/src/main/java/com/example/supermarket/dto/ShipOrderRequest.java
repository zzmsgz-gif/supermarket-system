package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ShipOrderRequest {

    @NotBlank(message = "Ship company is required")
    @Size(max = 50, message = "Ship company must be at most 50 characters")
    private String shipCompany;

    @NotBlank(message = "Tracking number is required")
    @Size(max = 64, message = "Tracking number must be at most 64 characters")
    private String shipNo;

    public String getShipCompany() {
        return shipCompany;
    }

    public void setShipCompany(String shipCompany) {
        this.shipCompany = shipCompany;
    }

    public String getShipNo() {
        return shipNo;
    }

    public void setShipNo(String shipNo) {
        this.shipNo = shipNo;
    }
}
