package com.example.supermarket.service;

import com.example.supermarket.dto.AuthResponse;
import com.example.supermarket.dto.LoginRequest;
import com.example.supermarket.dto.ProfileUpdateRequest;
import com.example.supermarket.dto.RegisterRequest;
import com.example.supermarket.dto.UserResponse;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.repository.SysUserRepository;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.security.JwtService;
import com.example.supermarket.service.CouponService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final byte ENABLED = 1;
    private static final byte NOT_DELETED = 0;
    private static final String ROLE_USER = "USER";

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CouponService couponService;

    public AuthService(
            SysUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CouponService couponService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.couponService = couponService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameAndDeleted(username, NOT_DELETED)) {
            throw new BusinessException(409, "Username already exists");
        }

        String phone = normalizeBlank(request.getPhone());
        if (phone != null && userRepository.existsByPhoneAndDeleted(phone, NOT_DELETED)) {
            throw new BusinessException(409, "Phone already exists");
        }

        String email = normalizeBlank(request.getEmail());
        if (email != null && userRepository.existsByEmailAndDeleted(email, NOT_DELETED)) {
            throw new BusinessException(409, "Email already exists");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(normalizeBlank(request.getNickname()) == null ? username : request.getNickname().trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(ROLE_USER);
        user.setStatus(ENABLED);
        user.setBalance(BigDecimal.ZERO);
        user.setDeleted(NOT_DELETED);
        SysUser saved = userRepository.save(user);

        // 注册即发新人券（best-effort，券配置缺失或发放失败都不影响注册成功）
        try {
            couponService.issueNewUserCoupon(saved.getId());
        } catch (Exception ignored) {
            // 发放新人券失败不应阻断注册
        }

        CurrentUser currentUser = new CurrentUser(saved);
        return new AuthResponse(jwtService.generateToken(currentUser), UserResponse.from(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsernameAndDeleted(request.getUsername().trim(), NOT_DELETED)
                .orElseThrow(() -> new BusinessException(401, "Username or password is incorrect"));
        if (!ENABLED_STATUS(user) || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "Username or password is incorrect");
        }

        user.setLastLoginAt(LocalDateTime.now());
        SysUser saved = userRepository.save(user);
        CurrentUser currentUser = new CurrentUser(saved);
        return new AuthResponse(jwtService.generateToken(currentUser), UserResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser currentUser) {
        return UserResponse.from(currentUser.getUser());
    }

    @Transactional
    public UserResponse updateProfile(CurrentUser currentUser, ProfileUpdateRequest request) {
        SysUser user = currentUser.getUser();
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (StringUtils.hasText(request.getPhone())) {
            String phone = request.getPhone().trim();
            if (!phone.equals(user.getPhone()) && userRepository.existsByPhoneAndDeleted(phone, NOT_DELETED)) {
                throw new BusinessException(409, "手机号已被其他账号使用");
            }
            user.setPhone(phone);
        }
        if (StringUtils.hasText(request.getEmail())) {
            String email = request.getEmail().trim();
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndDeleted(email, NOT_DELETED)) {
                throw new BusinessException(409, "邮箱已被其他账号使用");
            }
            user.setEmail(email);
        }
        return UserResponse.from(userRepository.save(user));
    }

    private boolean ENABLED_STATUS(SysUser user) {
        return Byte.valueOf(ENABLED).equals(user.getStatus());
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
