package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CreateOrderRequest;
import com.example.supermarket.dto.OrderResponse;
import com.example.supermarket.dto.RefundApplyRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.IdempotencyService;
import com.example.supermarket.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        // 幂等：相同 Idempotency-Key 在有效期内重复提交直接返回首次结果，避免重复下单
        return idempotencyService.execute(idempotencyKey,
                () -> ApiResponse.ok(orderService.createOrder(currentUser.getId(), request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> listOrders(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(orderService.listOrders(currentUser.getId(), page, size, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(orderService.getOrder(currentUser.getId(), id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderResponse> payOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(orderService.payOrder(currentUser.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(orderService.cancelOrder(currentUser.getId(), id));
    }

    @PostMapping("/{id}/confirm-receipt")
    public ApiResponse<OrderResponse> confirmReceipt(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(orderService.confirmReceipt(currentUser.getId(), id));
    }

    @PostMapping("/{id}/refund-apply")
    public ApiResponse<OrderResponse> applyRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundApplyRequest request
    ) {
        return ApiResponse.ok(orderService.applyRefund(currentUser.getId(), id, request.getReason()));
    }
}
