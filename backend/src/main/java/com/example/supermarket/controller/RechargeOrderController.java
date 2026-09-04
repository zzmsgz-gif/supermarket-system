package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.RechargeOrderCreateRequest;
import com.example.supermarket.dto.RechargeOrderResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.RechargeOrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/wallet/recharge-orders")
public class RechargeOrderController {

    private final RechargeOrderService rechargeOrderService;

    public RechargeOrderController(RechargeOrderService rechargeOrderService) {
        this.rechargeOrderService = rechargeOrderService;
    }

    @PostMapping
    public ApiResponse<RechargeOrderResponse> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody RechargeOrderCreateRequest request
    ) {
        return ApiResponse.ok(rechargeOrderService.createOrder(currentUser.getId(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<RechargeOrderResponse> get(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(rechargeOrderService.getOrder(currentUser.getId(), id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<RechargeOrderResponse> pay(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(rechargeOrderService.pay(currentUser.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RechargeOrderResponse> cancel(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(rechargeOrderService.cancel(currentUser.getId(), id));
    }
}
