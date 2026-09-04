package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ProductDetailResponse;
import com.example.supermarket.dto.ProductSummaryResponse;
import com.example.supermarket.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.ok(productService.searchProducts(page, size, categoryId, keyword, minPrice, maxPrice, brand, sort));
    }

    @GetMapping("/hot")
    public ApiResponse<List<ProductSummaryResponse>> listHot(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        return ApiResponse.ok(productService.listHot(limit));
    }

    @GetMapping("/new")
    public ApiResponse<List<ProductSummaryResponse>> listNew(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        return ApiResponse.ok(productService.listNew(limit));
    }

    @GetMapping("/related/{id}")
    public ApiResponse<List<ProductSummaryResponse>> listRelated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit
    ) {
        return ApiResponse.ok(productService.listRelated(id, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.ok(productService.getPublicProduct(id));
    }
}
