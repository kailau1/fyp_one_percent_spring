package com.example.one_percent.security;

import com.example.one_percent.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";

    private JwtUtil jwtUtil;
    private final User user = User.builder().id("u1").email("kai@example.com").firstName("Kai").lastName("L").build();

    @BeforeEach
    void setUp() {
        jwtUtil = jwtUtilWithSecret(SECRET);
    }

    private static JwtUtil jwtUtilWithSecret(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expirationDays", 7L);
        return util;
    }

    @Test
    void tokenRoundTripsTheEmail() {
        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("kai@example.com");
    }

    @Test
    void tokenCarriesTheClaimsTheAppReads() {
        String token = jwtUtil.generateToken(user);

        var claims = Jwts.parserBuilder().setSigningKey(SECRET.getBytes()).build().parseClaimsJws(token).getBody();
        assertThat(claims.get("userId")).isEqualTo("u1");
        assertThat(claims.get("firstName")).isEqualTo("Kai");
        assertThat(claims.get("lastName")).isEqualTo("L");
    }

    @Test
    void tokenExpiresAfterTheConfiguredNumberOfDays() {
        String token = jwtUtil.generateToken(user);

        var claims = Jwts.parserBuilder().setSigningKey(SECRET.getBytes()).build().parseClaimsJws(token).getBody();
        long lifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(lifetimeMs).isEqualTo(java.time.Duration.ofDays(7).toMillis());
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expirationDays", -1L); // already expired when issued
        String expired = jwtUtil.generateToken(user);

        assertThatThrownBy(() -> jwtUtil.extractEmail(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        String forged = jwtUtilWithSecret("a-completely-different-secret-of-32-bytes!!").generateToken(user);

        assertThatThrownBy(() -> jwtUtil.extractEmail(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtUtil.generateToken(user).split("\\.");
        String evilPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"victim@example.com\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + evilPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.extractEmail(tampered)).isInstanceOf(JwtException.class);
    }
}
