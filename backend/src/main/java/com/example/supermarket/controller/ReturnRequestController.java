package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ReturnApplyRequest;
import com.example.supermarket.dto.ReturnRequestResponse;
import com.example.supermarket.dto.ReturnShipRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.ReturnRequestService;
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

/** 售后/退货退款 —— 用户端（第 13 条） */
@Validated
@RestController
@RequestMapping("/returns")
public class ReturnRequestController {

    private final ReturnRequestService service;

    public ReturnRequestController(ReturnRequestService service) {
        this.service = service;
    }

    /** 我的售后列表 */
    @GetMapping
    public ApiResponse<PageResponse<ReturnRequestResponse>> listMine(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(service.listMine(currentUser.getId(), page, size));
    }

    /** 某订单的当前售后单（订单详情页用） */
    @GetMapping("/order/{orderId}")
    public ApiResponse<ReturnRequestResponse> getByOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable("orderId") Long orderId
    ) {
        return ApiResponse.ok(service.getByOrder(currentUser.getId(), orderId));
    }

    /** 提交售后申请 */
    @PostMapping
    public ApiResponse<ReturnRequestResponse> apply(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ReturnApplyRequest req
    ) {
        return ApiResponse.ok(service.apply(currentUser.getId(), req));
    }

    /** 填写寄回信息（快递公司 + 运单号） */
    @PostMapping("/{id}/ship-back")
    public ApiResponse<ReturnRequestResponse> shipBack(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody ReturnShipRequest req
    ) {
        return ApiResponse.ok(service.shipBack(currentUser.getId(), id, req));
    }
}
