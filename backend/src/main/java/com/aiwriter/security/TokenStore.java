package com.aiwriter.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token 白名单（Redis）。
 *
 * <p>为什么需要它：JWT 是无状态的，服务端"签出去就管不了"——用户改密码、
 * 发现设备丢失、或者想强制下线，都只能等 token 自然过期（这里是 7 天）。
 * 加一层白名单后，refresh 的 jti 必须在 Redis 里存在才算有效，删掉即失效。
 *
 * <p>降级策略：**Redis 不可用时放行**（退化成原来的纯无状态行为），
 * 保证缓存故障不至于让所有人登录不了；代价是那段时间无法强制下线。
 */
@Component
public class TokenStore {

    private static final Logger log = LoggerFactory.getLogger(TokenStore.class);
    private static final String PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public TokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 签发 refresh 时登记 jti，TTL 与 token 有效期一致。 */
    public void save(Long userId, String jti, Duration ttl) {
        if (userId == null || jti == null) return;
        try {
            redis.opsForValue().set(key(userId, jti), "1", ttl);
        } catch (RuntimeException e) {
            log.warn("登记 refresh 白名单失败（Redis 不可用?）userId={}: {}", userId, e.toString());
        }
    }

    /** 校验 jti 是否仍然有效；Redis 故障时返回 true（放行）。 */
    public boolean isActive(Long userId, String jti) {
        if (userId == null || jti == null) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(userId, jti)));
        } catch (RuntimeException e) {
            log.warn("校验 refresh 白名单失败（Redis 不可用?），降级放行 userId={}: {}", userId, e.toString());
            return true;
        }
    }

    /**
     * 原子地"消费"一个 jti：删掉它，并告诉调用方是不是**我**删掉的。
     *
     * <p>为什么不能用「先 hasKey 再 delete」：两个并发刷新会同时看到 key 存在，
     * 都通过校验、都换发新 token——等于轮换失效。DEL 在 Redis 里是单线程原子操作，
     * 返回值只可能给一个调用方 true，用它来抢这一次轮换权。
     *
     * <p>Redis 故障时返回 true（降级放行），保持与 {@link #isActive} 一致的可用性策略。
     */
    public boolean consume(Long userId, String jti) {
        if (userId == null || jti == null) return false;
        try {
            Boolean removed = redis.delete(key(userId, jti));
            return Boolean.TRUE.equals(removed);
        } catch (RuntimeException e) {
            log.warn("消费 refresh 失败（Redis 不可用?），降级放行 userId={}: {}", userId, e.toString());
            return true;
        }
    }

    /** 作废单个 jti：登出、或者刷新时轮换旧 token。 */
    public void revoke(Long userId, String jti) {
        if (userId == null || jti == null) return;
        try {
            redis.delete(key(userId, jti));
        } catch (RuntimeException e) {
            log.warn("作废 refresh 失败（Redis 不可用?）userId={}: {}", userId, e.toString());
        }
    }

    private String key(Long userId, String jti) {
        return PREFIX + userId + ":" + jti;
    }
}
