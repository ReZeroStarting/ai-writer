package com.aiwriter.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /** token 类型标识 claim，防止 access / refresh 双向冒用。 */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 环境变量未设置，拒绝启动");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, UUID.randomUUID().toString());
    }

    /**
     * 带 jti 的 refresh token。
     *
     * <p>jti 是 Redis 白名单的键：签发时登记，刷新时校验并轮换，
     * 这样即使 token 没过期，也能被服务端主动作废。
     */
    public String generateRefreshToken(Long userId, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /** 取 refresh token 的 jti（白名单键）。解析失败返回 null。 */
    public String getJti(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getId();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** refresh token 的有效期，用于给 Redis 白名单设同样的 TTL。 */
    public Duration refreshTokenTtl() {
        return Duration.ofMillis(refreshTokenExpiration);
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(
                Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token).getPayload().getSubject()
        );
    }

    /** 通用校验：签名有效且未过期（不区分类型）。 */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 严格校验：必须是 access token。用于认证过滤器，杜绝拿 refresh 当 access 用。 */
    public boolean validateAccessToken(String token) {
        return validateTokenOfType(token, TYPE_ACCESS);
    }

    /** 严格校验：必须是 refresh token。用于刷新接口。 */
    public boolean validateRefreshToken(String token) {
        return validateTokenOfType(token, TYPE_REFRESH);
    }

    private boolean validateTokenOfType(String token, String expectedType) {
        try {
            String actual = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().get(CLAIM_TYPE, String.class);
            return expectedType.equals(actual);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
