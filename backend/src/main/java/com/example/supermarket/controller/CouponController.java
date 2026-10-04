package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CouponResponse;
import com.example.supermarket.dto.UserCouponResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.CouponService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/available")
    public ApiResponse<List<CouponResponse>> listAvailableCoupons(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(couponService.listAvailableCoupons(currentUser.getId()));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<UserCouponResponse> receiveCoupon(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(couponService.receiveCoupon(currentUser.getId(), id));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<UserCouponResponse>> listMyCoupons(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(couponService.listMyCoupons(currentUser.getId(), page, size));
    }

    @GetMapping("/usable")
    public ApiResponse<List<UserCouponResponse>> listUsableCoupons(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam BigDecimal amount
    ) {
        return ApiResponse.ok(couponService.listUsableCoupons(currentUser.getId(), amount));
    }
}
