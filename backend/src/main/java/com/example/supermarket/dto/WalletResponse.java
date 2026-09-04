package com.example.supermarket.dto;

import java.math.BigDecimal;
import java.util.List;

public class WalletResponse {

    private BigDecimal balance;
    private List<WalletTransactionResponse> recentTransactions;

    public WalletResponse() {
    }

    public WalletResponse(BigDecimal balance, List<WalletTransactionResponse> recentTransactions) {
        this.balance = balance;
        this.recentTransactions = recentTransactions;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public List<WalletTransactionResponse> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<WalletTransactionResponse> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }
}
