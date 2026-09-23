package com.example.supermarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET = "supermarket-local-dev-secret-change-me-32-bytes";
    private static final long DEFAULT_TTL = 3600;
    private static final long REMEMBER_TTL = 604800;

    private final JwtService jwtService = new JwtService(SECRET, DEFAULT_TTL, REMEMBER_TTL);

    private CurrentUser user(String name) {
        CurrentUser user = mock(CurrentUser.class);
        when(user.getUsername()).thenReturn(name);
        return user;
    }

    @Test
    void generateAndExtractRoundTrip() {
        CurrentUser alice = user("alice");
        when(alice.getId()).thenReturn(1L);
        when(alice.getRole()).thenReturn("USER");

        String token = jwtService.generateToken(alice);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isValidReflectsSubjectAndExpiry() {
        CurrentUser alice = user("alice");
        String token = jwtService.generateToken(alice);
        assertThat(jwtService.isValid(token, alice)).isTrue();
        assertThat(jwtService.isValid(token, user("bob"))).isFalse();
    }

    /** 不勾「记住我」→ 默认有效期；勾了 → 更长的有效期（两条链路都要有断言，别只测一条）。 */
    @Test
    void rememberFlagControlsTtl() {
        long plainTtl = ttlSeconds(jwtService.generateToken(user("alice")));
        long rememberTtl = ttlSeconds(jwtService.generateToken(user("alice"), true));

        assertThat(plainTtl).isEqualTo(DEFAULT_TTL);
        assertThat(rememberTtl).isEqualTo(REMEMBER_TTL);
        assertThat(rememberTtl).isGreaterThan(plainTtl);
    }

    private long ttlSeconds(String token) {
        Claims claims = Jwts.parser().verifyWith(
                        io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        Date issued = claims.getIssuedAt();
        Date expires = claims.getExpiration();
        return Duration.between(issued.toInstant(), expires.toInstant()).getSeconds();
    }
}
