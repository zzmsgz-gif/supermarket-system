package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AuthResponse;
import com.example.supermarket.dto.LoginRequest;
import com.example.supermarket.dto.ProfileUpdateRequest;
import com.example.supermarket.dto.RegisterRequest;
import com.example.supermarket.dto.UserResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AuthService;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
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

}
