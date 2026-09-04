package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ReviewCreateRequest;
import com.example.supermarket.dto.ReviewResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/orders/{orderId}")
    public ApiResponse<List<ReviewResponse>> createOrderReview(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ApiResponse.ok(reviewService.createOrderReview(currentUser.getId(), orderId, request));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<List<ReviewResponse>> listOrderReviews(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long orderId
    ) {
        return ApiResponse.ok(reviewService.listOrderReviews(currentUser.getId(), orderId));
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<PageResponse<ReviewResponse>> listProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(reviewService.listProductReviews(productId, page, size));
    }
}
