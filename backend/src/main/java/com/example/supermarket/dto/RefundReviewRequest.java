package com.example.supermarket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RefundReviewRequest {

    @NotNull(message = "Approved flag is required")
    private Boolean approved;

    @Size(max = 255, message = "Remark must be at most 255 characters")
    private String remark;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
