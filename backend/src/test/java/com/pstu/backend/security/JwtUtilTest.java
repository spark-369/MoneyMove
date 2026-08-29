package com.pstu.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret-test-secret-test-secret", 3600_000L);
        userDetails = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("x").authorities("ROLE_USER").build();
    }

    @Test
    void generateTokenContainsCorrectUsername() {
        String token = jwtUtil.generateToken("alice");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void validTokenPassesValidation() {
        String token = jwtUtil.generateToken("alice");
        assertThat(jwtUtil.validateToken(token, userDetails)).isTrue();
    }

    @Test
    void tokenFailsValidationForWrongUser() {
        String token = jwtUtil.generateToken("alice");
        UserDetails other = org.springframework.security.core.userdetails.User.withUsername("bob")
                .password("x").authorities("ROLE_USER").build();
        assertThat(jwtUtil.validateToken(token, other)).isFalse();
    }

    @Test
    void expiredTokenFailsValidation() {
        JwtUtil shortLived = new JwtUtil("test-secret-test-secret-test-secret-test-secret-test-secret-test-secret", -1000L);
        String token = shortLived.generateToken("alice");
        assertThatThrownBy(() -> shortLived.validateToken(token, userDetails))
                .isInstanceOf(Exception.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateToken("alice");
        String tampered = token + "x";
        assertThatThrownBy(() -> jwtUtil.validateToken(tampered, userDetails))
                .isInstanceOf(Exception.class);
    }
}
