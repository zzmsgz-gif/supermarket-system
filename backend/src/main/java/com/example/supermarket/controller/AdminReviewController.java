package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminReviewReplyRequest;
import com.example.supermarket.dto.AdminReviewResponse;
import com.example.supermarket.dto.AdminReviewSummary;
import com.example.supermarket.service.AdminReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台评价管理（`/admin/**` 由 SecurityConfig 统一要求 ROLE_ADMIN）。
 */
@Validated
@RestController
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    /** rating 按星级筛选；replied 区分已回复/未回复（未回复才是待办）；keyword 搜评价正文 */
    @GetMapping
    public ApiResponse<PageResponse<AdminReviewResponse>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean replied,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(adminReviewService.list(page, size, rating, replied, keyword));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminReviewSummary> summary() {
        return ApiResponse.ok(adminReviewService.summary());
    }

    /** 回复评价（空字符串＝撤回回复）；回复会立刻出现在前台商品详情页 */
    @PostMapping("/{id}/reply")
    public ApiResponse<AdminReviewResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody AdminReviewReplyRequest request
    ) {
        return ApiResponse.ok(adminReviewService.reply(id, request.getReplyContent()));
    }

    /** 违规隐藏 / 恢复展示（不物理删除，订单仍算已评价） */
    @PostMapping("/{id}/hidden")
    public ApiResponse<AdminReviewResponse> setHidden(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        boolean hidden = body.get("hidden") != null && Boolean.parseBoolean(String.valueOf(body.get("hidden")));
        return ApiResponse.ok(adminReviewService.setHidden(id, hidden));
    }
}
