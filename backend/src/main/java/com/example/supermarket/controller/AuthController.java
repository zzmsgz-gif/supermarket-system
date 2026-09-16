package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AuthResponse;
import com.example.supermarket.dto.ChangePasswordRequest;
import com.example.supermarket.dto.LoginRequest;
import com.example.supermarket.dto.PasswordResetSubmitRequest;
import com.example.supermarket.dto.PasswordResetSubmitResponse;
import com.example.supermarket.dto.ProfileUpdateRequest;
import com.example.supermarket.dto.RegisterRequest;
import com.example.supermarket.dto.UserResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AuthService;
import com.example.supermarket.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.me(currentUser));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return ApiResponse.ok(authService.updateProfile(currentUser, request));
    }

    /**
     * 忘记密码：提交找回申请（游客可调，已加入 SecurityConfig permitAll）。
     * 返回文案对「账号存在 / 不存在」完全一致，避免被当成账号探测器。
     */
    @PostMapping("/password-reset-request")
    public ApiResponse<PasswordResetSubmitResponse> submitPasswordReset(
            @Valid @RequestBody PasswordResetSubmitRequest request
    ) {
        return ApiResponse.ok(passwordResetService.submit(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUser, request);
        return ApiResponse.ok(null);
    }

}
