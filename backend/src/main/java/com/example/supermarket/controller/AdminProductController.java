package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminProductCreateRequest;
import com.example.supermarket.dto.AdminProductUpdateRequest;
import com.example.supermarket.dto.ProductDetailResponse;
import com.example.supermarket.dto.ProductStatusRequest;
import com.example.supermarket.dto.ProductSummaryResponse;
import com.example.supermarket.dto.StockAdjustmentRequest;
import com.example.supermarket.dto.StockLogResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AdminProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(adminProductService.listProducts(page, size, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.ok(adminProductService.getProduct(id));
    }

    @PostMapping
    public ApiResponse<ProductDetailResponse> createProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody AdminProductCreateRequest request
    ) {
        return ApiResponse.ok(adminProductService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminProductUpdateRequest request
    ) {
        return ApiResponse.ok(adminProductService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductDetailResponse> updateStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusRequest request
    ) {
        return ApiResponse.ok(adminProductService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        adminProductService.deleteProduct(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/stock-adjustments")
    public ApiResponse<StockLogResponse> adjustStock(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody StockAdjustmentRequest request
    ) {
        return ApiResponse.ok(adminProductService.adjustStock(id, currentUser.getId(), request));
    }
}
