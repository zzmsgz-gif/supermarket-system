package com.example.supermarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("supermarket-local-dev-secret-change-me-32-bytes", 3600);

    @Test
    void generateAndExtractRoundTrip() {
        CurrentUser user = mock(CurrentUser.class);
        when(user.getUsername()).thenReturn("alice");
        when(user.getId()).thenReturn(1L);
        when(user.getRole()).thenReturn("USER");

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isValidReflectsSubjectAndExpiry() {
        CurrentUser user = mock(CurrentUser.class);
        when(user.getUsername()).thenReturn("alice");

        String token = jwtService.generateToken(user);
        assertThat(jwtService.isValid(token, user)).isTrue();

        CurrentUser other = mock(CurrentUser.class);
        when(other.getUsername()).thenReturn("bob");
        assertThat(jwtService.isValid(token, other)).isFalse();
    }
}
