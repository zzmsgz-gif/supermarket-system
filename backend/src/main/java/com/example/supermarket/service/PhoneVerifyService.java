package com.example.supermarket.service;

import com.example.supermarket.exception.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 手机号换绑验证（第 9 条）。
 *
 * <p><b>为什么必须有这一步</b>：手机号是账号的登录标识与找回通道。
 * 无验证改绑意味着任何拿到登录态的人都能把手机号改成自己的，
 * 之后用「忘记密码」就能把账号彻底拿走 —— 这是账号被盗，不是体验问题。
 *
 * <p><b>当前实现</b>：项目**没有短信通道**（密码重置走的是管理员生成临时密码），
 * 所以这里不造假验证：{@link #requestCode} 会把验证码通过
 * {@link #codeSink}（默认打到日志）交出去，并把「验证码 → token」记在内存里。
 * 接入真实短信/邮箱通道后，只需替换 {@link #deliver} 的投递方式，
 * 签发与校验逻辑一行不用动 —— 前端也不需要改，因为它只认 verifyToken。
 *
 * <p><b>生产注意</b>：内存 Map 只适合单实例。多实例部署必须换成 Redis，
 * 否则 A 实例签发的 token 到 B 实例校验必然失败（用户表现为「验证码总是错」）。
 */
@Service
public class PhoneVerifyService {

    /** 验证码 → 目标手机号 */
    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();

    /** token → 归属（用户 + 手机号） */
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_TTL_MINUTES = 10;
    private static final int TOKEN_TTL_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 验证码投递出口。项目没接短信时这里是日志；
     * 接入真实通道后改成发短信/邮件，并**不要**把验证码返回给前端。
     */
    @Value("${app.phone-verify.code-sink:log}")
    private String codeSink;

    /**
     * 申请验证码。
     *
     * @return 本次生成的验证码（仅当 codeSink=log 时有意义，供本地联调）
     */
    public String requestCode(Long userId, String newPhone) {
        if (newPhone == null || !newPhone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        // 同一个手机号反复申请会变成短信轰炸入口 —— 按目标号限流
        long recent = pending.values().stream()
                .filter(p -> p.phone.equals(newPhone) && !p.expired())
                .count();
        if (recent >= 3) {
            throw new BusinessException(429, "验证码发送过于频繁，请稍后再试。");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        pending.put(code, new PendingCode(userId, newPhone, LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES)));
        deliver(newPhone, code);
        return codeSink.equals("log") ? code : null;
    }

    /**
     * 校验验证码并签发一次性换绑 token。
     */
    public String verify(Long userId, String newPhone, String code) {
        PendingCode p = pending.get(code == null ? "" : code.trim());
        if (p == null) {
            throw new BusinessException(400, "验证码不正确或已失效。");
        }
        if (p.expired()) {
            pending.remove(code.trim());
            throw new BusinessException(400, "验证码已过期，请重新获取。");
        }
        if (!p.userId.equals(userId) || !p.phone.equals(newPhone)) {
            p.attempts++;
            if (p.attempts >= MAX_ATTEMPTS) {
                pending.remove(code.trim());
            }
            throw new BusinessException(400, "验证码与申请的手机号不匹配。");
        }
        // 一次性：用掉即删
        pending.remove(code.trim());
        String token = "pv_" + Long.toHexString(RANDOM.nextLong()) + Long.toHexString(RANDOM.nextLong());
        tokens.put(token, new TokenEntry(userId, newPhone,
                LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES)));
        return token;
    }

    /** 更新资料时校验 token：必须是**该用户**为**该手机号**换来的、且未过期 */
    public boolean isValidToken(Long userId, String newPhone, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        TokenEntry e = tokens.get(token.trim());
        if (e == null) {
            return false;
        }
        if (e.expired()) {
            tokens.remove(token.trim());
            return false;
        }
        if (!e.userId.equals(userId) || !e.phone.equals(newPhone)) {
            return false;
        }
        tokens.remove(token.trim());   // 一次性
        return true;
    }

    private void deliver(String phone, String code) {
        if (codeSink.equals("log")) {
            org.slf4j.LoggerFactory.getLogger(PhoneVerifyService.class)
                    .warn("[手机号验证] 目标 {} 的验证码是 {}（未接短信通道，暂记日志）", phone, code);
        }
        // 接入真实通道后在这里投递（短信/邮件），不要把 code 返回给前端
    }

    /** 待校验的验证码（普通类而非 record：attempts 需要可变，record 是不可变的） */
    private static final class PendingCode {
        private final Long userId;
        private final String phone;
        private final LocalDateTime expireAt;
        private int attempts;

        PendingCode(Long userId, String phone, LocalDateTime expireAt) {
            this.userId = userId;
            this.phone = phone;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return LocalDateTime.now().isAfter(expireAt);
        }
    }

    private static final class TokenEntry {
        private final Long userId;
        private final String phone;
        private final LocalDateTime expireAt;

        TokenEntry(Long userId, String phone, LocalDateTime expireAt) {
            this.userId = userId;
            this.phone = phone;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return LocalDateTime.now().isAfter(expireAt);
        }
    }
}
