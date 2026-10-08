package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ReturnRequestResponse;
import com.example.supermarket.dto.ReturnReviewRequest;
import com.example.supermarket.service.ReturnRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台售后管理（第 13 条） */
@Validated
@RestController
@RequestMapping("/admin/return-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnRequestController {

    private final ReturnRequestService service;

    public AdminReturnRequestController(ReturnRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<ReturnRequestResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(service.listAll(status, page, size));
    }

    /**
     * 审核。
     *
     * <p>关键差异（相对旧的 refund-review）：**同意不再等于立刻退款**。
     * needReturn=true 时只把状态推进到「待寄回」，等用户寄回、商家确认收货后才放款。
     */
    @PostMapping("/{id}/review")
    public ApiResponse<ReturnRequestResponse> review(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReturnReviewRequest req
    ) {
        return ApiResponse.ok(service.review(id, req));
    }

    /** 确认收到退货 → 放款 */
    @PostMapping("/{id}/confirm-received")
    public ApiResponse<ReturnRequestResponse> confirmReceived(
            @PathVariable("id") Long id,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        String remark = body == null ? null : body.get("remark");
        return ApiResponse.ok(service.confirmReceived(id, remark));
    }
}
