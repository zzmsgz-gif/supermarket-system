package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.OrderResponse;
import com.example.supermarket.dto.RefundReviewRequest;
import com.example.supermarket.dto.ShipOrderRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AdminOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> listOrders(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String refundStatus
    ) {
        return ApiResponse.ok(adminOrderService.listOrders(page, size, status, userId, orderNo, refundStatus));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(adminOrderService.getOrder(id));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<OrderResponse> shipOrder(
            @PathVariable Long id,
            @Valid @RequestBody ShipOrderRequest request
    ) {
        return ApiResponse.ok(adminOrderService.shipOrder(id, request.getShipCompany().trim(), request.getShipNo().trim()));
    }

    @PostMapping("/{id}/refund-review")
    public ApiResponse<OrderResponse> reviewRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundReviewRequest request
    ) {
        return ApiResponse.ok(adminOrderService.reviewRefund(id, currentUser.getId(),
                Boolean.TRUE.equals(request.getApproved()), request.getRemark()));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        return ApiResponse.ok(adminOrderService.completeOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(adminOrderService.cancelOrder(id, currentUser.getId()));
    }
}
