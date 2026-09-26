package com.example.supermarket.service;

import com.example.supermarket.dto.AuthResponse;
import com.example.supermarket.dto.ChangePasswordRequest;
import com.example.supermarket.dto.LoginRequest;
import com.example.supermarket.dto.ProfileUpdateRequest;
import com.example.supermarket.dto.RegisterRequest;
import com.example.supermarket.dto.UserResponse;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.UserMessage;
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
import com.example.supermarket.dto.WechatLoginRequest;
import java.util.UUID;

@Service
public class AuthService {

    private static final byte ENABLED = 1;
    private static final byte NOT_DELETED = 0;
    private static final String ROLE_USER = "USER";

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CouponService couponService;
    private final MessageService messageService;
    private final WechatService wechatService;

    public AuthService(
            SysUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CouponService couponService,
            MessageService messageService,
            WechatService wechatService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.couponService = couponService;
        this.messageService = messageService;
        this.wechatService = wechatService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameAndDeleted(username, NOT_DELETED)) {
            throw new BusinessException(409, "用户名已存在");
        }

        String phone = normalizeBlank(request.getPhone());
        if (phone != null && userRepository.existsByPhoneAndDeleted(phone, NOT_DELETED)) {
            throw new BusinessException(409, "手机号已被注册");
        }

        String email = normalizeBlank(request.getEmail());
        if (email != null && userRepository.existsByEmailAndDeleted(email, NOT_DELETED)) {
            throw new BusinessException(409, "邮箱已被注册");
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

        issueWelcome(saved);

        CurrentUser currentUser = new CurrentUser(saved);
        return new AuthResponse(jwtService.generateToken(currentUser), UserResponse.from(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsernameAndDeleted(request.getUsername().trim(), NOT_DELETED)
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!ENABLED_STATUS(user) || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        SysUser saved = userRepository.save(user);
        CurrentUser currentUser = new CurrentUser(saved);
        // 「记住我」勾了 → 7 天有效的 token（默认 24 小时）；前端据此决定 token 存 localStorage 还是 sessionStorage。
        // ⚠️ 注册自动登录不勾（没有那个复选框），走默认有效期。
        return new AuthResponse(jwtService.generateToken(currentUser, Boolean.TRUE.equals(request.getRemember())),
                UserResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser currentUser) {
        return UserResponse.from(currentUser.getUser());
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        SysUser user = currentUser.getUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // 改密成功即解除「强制改密」标记：管理员发的临时密码到此失效
        user.setMustChangePassword((byte) 0);
        userRepository.save(user);
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

    /**
     * 微信小程序登录：用 code 换 openid，按 openid 查账号；没有则自动注册独立账号。
     * 复用现有 JwtService 签发与 PC 完全一致的 token，因此小程序用户与 PC 用户共享同一套鉴权。
     */
    @Transactional
    public AuthResponse wechatLogin(WechatLoginRequest request) {
        String openid = wechatService.code2Session(request.getCode());
        SysUser user = userRepository.findByWxOpenidAndDeleted(openid, NOT_DELETED).orElse(null);
        if (user == null) {
            // 首次微信登录：自动注册独立账号（username 用 wx_<openid> 保证唯一；密码随机，仅供 DB 非空约束）
            SysUser newUser = new SysUser();
            newUser.setUsername("wx_" + openid);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : "微信用户");
            newUser.setAvatarUrl(request.getAvatarUrl());
            newUser.setWxOpenid(openid);
            newUser.setRole(ROLE_USER);
            newUser.setStatus(ENABLED);
            newUser.setBalance(BigDecimal.ZERO);
            newUser.setDeleted(NOT_DELETED);
            SysUser saved = userRepository.save(newUser);
            issueWelcome(saved);
            user = saved;
        } else {
            user.setLastLoginAt(LocalDateTime.now());
            if (StringUtils.hasText(request.getNickname())) {
                user.setNickname(request.getNickname().trim());
            }
            if (request.getAvatarUrl() != null) {
                user.setAvatarUrl(request.getAvatarUrl());
            }
            user = userRepository.save(user);
        }
        CurrentUser currentUser = new CurrentUser(user);
        return new AuthResponse(jwtService.generateToken(currentUser), UserResponse.from(user));
    }

    private void issueWelcome(SysUser saved) {
        // 注册即发新人券（best-effort，券配置缺失或发放失败都不影响注册成功）
        try {
            couponService.issueNewUserCoupon(saved.getId());
        } catch (Exception ignored) {
            // 发放新人券失败不应阻断注册
        }
        // 欢迎消息（消息中心的第一条；dedupeKey 保证重复注册调用也只落一条）
        messageService.push(saved.getId(), UserMessage.TYPE_SYSTEM, "欢迎加入超市购物系统",
                "新人专享券已发放到你的账户，下单立减。收藏商品后降价还会第一时间提醒你。",
                "coupons", null, "WELCOME:" + saved.getId());
    }

    private boolean ENABLED_STATUS(SysUser user) {
        return Byte.valueOf(ENABLED).equals(user.getStatus());
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
