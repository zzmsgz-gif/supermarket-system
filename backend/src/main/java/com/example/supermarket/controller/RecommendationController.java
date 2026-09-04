package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.ProductSummaryResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final ProductService productService;

    public RecommendationController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/guess")
    public ApiResponse<List<ProductSummaryResponse>> guess(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ApiResponse.ok(productService.guessYouLike(userId, limit));
    }
}
