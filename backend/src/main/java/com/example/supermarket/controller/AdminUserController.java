package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminUserResponse;
import com.example.supermarket.dto.AdminUserUpdateRequest;
import com.example.supermarket.dto.UserRoleRequest;
import com.example.supermarket.dto.UserStatusRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Byte status
    ) {
        return ApiResponse.ok(adminUserService.listUsers(page, size, keyword, role, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.getUser(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return ApiResponse.ok(adminUserService.updateUser(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request
    ) {
        return ApiResponse.ok(adminUserService.updateStatus(id, currentUser.getId(), request));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<AdminUserResponse> updateRole(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UserRoleRequest request
    ) {
        return ApiResponse.ok(adminUserService.updateRole(id, currentUser.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        adminUserService.deleteUser(id, currentUser.getId());
        return ApiResponse.ok();
    }
}
