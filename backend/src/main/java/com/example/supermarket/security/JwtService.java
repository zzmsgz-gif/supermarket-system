package com.example.supermarket.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;
    /** 「记住我」勾选后的有效期（默认 7 天），见 app.jwt.remember-expiration-seconds */
    private final long rememberExpirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds,
            @Value("${app.jwt.remember-expiration-seconds:604800}") long rememberExpirationSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.rememberExpirationSeconds = rememberExpirationSeconds;
    }

    public String generateToken(CurrentUser currentUser) {
        return generateToken(currentUser, false);
    }

    /**
     * 签发 token。
     *
     * @param remember 登录时勾了「记住我」→ 用更长的有效期（默认 7 天）；否则用默认有效期（默认 24 小时）。
     *                 ⚠️ 前端只有在勾选时才会把 token 放进 localStorage，不勾则放 sessionStorage（关浏览器即失效）——
     *                 两边要一起看才构成完整的「记住我」。
     */
    public String generateToken(CurrentUser currentUser, boolean remember) {
        Instant now = Instant.now();
        long ttlSeconds = remember ? rememberExpirationSeconds : expirationSeconds;
        return Jwts.builder()
                .subject(currentUser.getUsername())
                .claim("userId", currentUser.getId())
                .claim("role", currentUser.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token, CurrentUser currentUser) {
        Claims claims = parseClaims(token);
        return currentUser.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
