package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.RechargeRequest;
import com.example.supermarket.dto.WalletResponse;
import com.example.supermarket.dto.WalletTransactionResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletResponse> getWallet(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(walletService.getWallet(currentUser.getId()));
    }

    @PostMapping("/recharges")
    public ApiResponse<WalletResponse> recharge(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody RechargeRequest request
    ) {
        return ApiResponse.ok(walletService.recharge(currentUser.getId(), request));
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<WalletTransactionResponse>> listTransactions(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(walletService.listTransactions(currentUser.getId(), page, size));
    }
}
