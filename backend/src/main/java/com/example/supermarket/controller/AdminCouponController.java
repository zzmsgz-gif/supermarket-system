package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CouponCreateRequest;
import com.example.supermarket.dto.CouponResponse;
import com.example.supermarket.dto.CouponStatusRequest;
import com.example.supermarket.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CouponResponse>> listCoupons(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(couponService.listAdminCoupons(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<CouponResponse> createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.ok(couponService.createCoupon(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CouponResponse> updateCouponStatus(
            @PathVariable Long id,
            @Valid @RequestBody CouponStatusRequest request
    ) {
        return ApiResponse.ok(couponService.updateCouponStatus(id, request.getStatus()));
    }

    // ===== 编辑 / 删除（2026-10-11）=====

    /**
     * 修改优惠券。校验规则与新建一致（见 CouponService.updateCoupon），
     * 额外约束：已有人领取时不允许下调面额 / 抬高门槛 / 收紧总量。
     */
    @PutMapping("/{id}")
    public ApiResponse<CouponResponse> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody CouponCreateRequest request
    ) {
        return ApiResponse.ok(couponService.updateCoupon(id, request));
    }

    /**
     * 删除优惠券（软删）。已领到手的券不受影响，仍可正常使用。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ApiResponse.ok(null);
    }
}
