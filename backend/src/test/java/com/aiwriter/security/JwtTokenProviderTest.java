package com.aiwriter.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private final String secret = "this-is-a-test-secret-that-is-at-least-256-bits-long-for-hmac-sha256!!";
    private final long accessExpiration = 1800000L; // 30 min
    private final long refreshExpiration = 604800000L; // 7 days

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(secret, accessExpiration, refreshExpiration);
    }

    @Test
    void shouldRejectBlankSecret() {
        assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("", 1000, 1000);
        });
        assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("   ", 1000, 1000);
        });
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        JwtTokenProvider p = provider();
        String token = p.generateAccessToken(42L, "testuser");

        assertNotNull(token);
        assertTrue(p.validateToken(token));
        assertEquals(42L, p.getUserIdFromToken(token));
    }

    @Test
    void shouldGenerateAndValidateRefreshToken() {
        JwtTokenProvider p = provider();
        String token = p.generateRefreshToken(99L);

        assertNotNull(token);
        assertTrue(p.validateToken(token));
        assertEquals(99L, p.getUserIdFromToken(token));
    }

    @Test
    void shouldRejectInvalidToken() {
        JwtTokenProvider p = provider();
        assertFalse(p.validateToken("invalid.token.here"));
        assertFalse(p.validateToken(""));
        assertFalse(p.validateToken(null));
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtTokenProvider p = provider();
        String token = p.generateAccessToken(1L, "user");

        // tamper with the payload by appending characters
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";
        assertFalse(p.validateToken(tampered));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        JwtTokenProvider p1 = provider();
        JwtTokenProvider p2 = new JwtTokenProvider(
                "another-secret-key-that-is-different-and-also-long-enough-!!",
                accessExpiration, refreshExpiration);

        String token = p1.generateAccessToken(1L, "user");
        assertFalse(p2.validateToken(token));
    }

  }
