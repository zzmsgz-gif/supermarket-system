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
import com.example.supermarket.dto.WechatLoginRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AuthService;
import com.example.supermarket.service.PasswordResetService;
import jakarta.validation.Valid;
import com.example.supermarket.dto.PhoneVerifyRequest;
import com.example.supermarket.dto.PhoneVerifyTicketResponse;
import com.example.supermarket.service.PhoneVerifyService;
import java.util.Map;
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
    private final PhoneVerifyService phoneVerifyService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          PhoneVerifyService phoneVerifyService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.phoneVerifyService = phoneVerifyService;
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

    /**
     * 手机号换绑：申请验证码（第 9 条）。
     *
     * <p>项目暂无短信通道，devCode 只在 {@code app.phone-verify.code-sink=log}（默认）时返回，
     * 接入真实通道后应返回 null —— 前端已按「有值就提示、没值就等短信」处理。
     */
    @PostMapping("/phone-verify/request")
    public ApiResponse<PhoneVerifyTicketResponse> requestPhoneVerify(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PhoneVerifyRequest request
    ) {
        return ApiResponse.ok(new PhoneVerifyTicketResponse(
                phoneVerifyService.requestCode(currentUser.getId(), request.getPhone()), 600));
    }

    /** 手机号换绑：校验验证码，换取一次性 verifyToken（更新资料时带上） */
    @PostMapping("/phone-verify/confirm")
    public ApiResponse<Map<String, String>> confirmPhoneVerify(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PhoneVerifyRequest request
    ) {
        String token = phoneVerifyService.verify(currentUser.getId(), request.getPhone(), request.getCode());
        return ApiResponse.ok(Map.of("verifyToken", token));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUser, request);
        return ApiResponse.ok(null);
    }

    /**
     * 微信小程序一键登录：小程序端 wx.login 拿 code → 本接口换 openid → 自动注册/登录 → 返回 JWT。
     * 游客可调（已在 SecurityConfig permitAll）。返回的 token 与 PC 密码登录完全同款。
     */
    @PostMapping("/wechat-login")
    public ApiResponse<AuthResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.ok(authService.wechatLogin(request));
    }

}
