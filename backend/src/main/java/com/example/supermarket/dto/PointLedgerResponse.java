package com.example.supermarket.dto;

import com.example.supermarket.entity.PointLedger;
import java.time.LocalDateTime;

public class PointLedgerResponse {

    private Long id;
    private String type;
    private Long amount;
    private Long balanceAfter;
    private Long refOrderId;
    private String remark;
    private LocalDateTime createdAt;

    public static PointLedgerResponse from(PointLedger ledger) {
        PointLedgerResponse response = new PointLedgerResponse();
        response.setId(ledger.getId());
        response.setType(ledger.getType());
        response.setAmount(ledger.getAmount());
        response.setBalanceAfter(ledger.getBalanceAfter());
        response.setRefOrderId(ledger.getRefOrderId());
        response.setRemark(ledger.getRemark());
        response.setCreatedAt(ledger.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(Long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Long getRefOrderId() {
        return refOrderId;
    }

    public void setRefOrderId(Long refOrderId) {
        this.refOrderId = refOrderId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
