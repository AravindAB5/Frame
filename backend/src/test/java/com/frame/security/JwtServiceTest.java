package com.frame.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("dev-only-secret-key-change-me-please-32bytes-min", 60);

    @Test
    void issuedTokenParsesBackToTheSamePrincipal() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueToken(userId, "person@example.com");

        FrameUserPrincipal principal = jwtService.parse(token);

        assertThat(principal).isNotNull();
        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo("person@example.com");
    }

    @Test
    void malformedTokenParsesToNull() {
        assertThat(jwtService.parse("not-a-real-token")).isNull();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-key-that-is-long-enough", 60);
        String token = other.issueToken(UUID.randomUUID(), "person@example.com");

        assertThat(jwtService.parse(token)).isNull();
    }
}
