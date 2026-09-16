package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.PasswordResetItemResponse;
import com.example.supermarket.dto.PasswordResetRejectRequest;
import com.example.supermarket.dto.PasswordResetResultResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.PasswordResetService;
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

/**
 * 后台：找回密码申请（忘记密码的人工处理入口）。
 * 整个 /admin/** 已在 SecurityConfig 里限定 hasRole("ADMIN")。
 */
@Validated
@RestController
@RequestMapping("/admin/password-reset-requests")
public class AdminPasswordResetController {

    private final PasswordResetService passwordResetService;

    public AdminPasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PasswordResetItemResponse>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(passwordResetService.list(status, page, size));
    }

    /** 待处理数量：后台侧边菜单红点。 */
    @GetMapping("/pending-count")
    public ApiResponse<Long> pendingCount() {
        return ApiResponse.ok(passwordResetService.pendingCount());
    }

    /** 重置为临时密码：响应里的 tempPassword 只出现这一次。 */
    @PostMapping("/{id}/reset")
    public ApiResponse<PasswordResetResultResponse> reset(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(passwordResetService.reset(id, currentUser.getId()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<PasswordResetItemResponse> reject(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PasswordResetRejectRequest request
    ) {
        return ApiResponse.ok(passwordResetService.reject(id, currentUser.getId(),
                request == null ? null : request.getRemark()));
    }
}
