package com.aiwriter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 每用户接口限流（Redis 固定窗口）。
 *
 * <p>用 Redis 做而不是内存计数器，是因为限流必须跨实例一致：多实例部署时，
 * 各自内存记账等于把配额放大 N 倍。
 *
 * <p>降级策略：**Redis 不可用时放行**——限流是保护措施，不该成为新的故障点。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String PREFIX = "rl:";

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int requestsPerMinute;

    public RateLimitInterceptor(
            StringRedisTemplate redis,
            @Value("${rate-limit.enabled:true}") boolean enabled,
            @Value("${rate-limit.requests-per-minute:60}") int requestsPerMinute) {
        this.redis = redis;
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) return true;

        String identity = resolveIdentity(request);
        String key = PREFIX + identity + ":" + (System.currentTimeMillis() / 60_000);
        long count;
        try {
            Long value = redis.opsForValue().increment(key);
            count = value == null ? 0 : value;
            if (count == 1) {
                redis.expire(key, Duration.ofMinutes(1));
            }
        } catch (RuntimeException e) {
            log.warn("限流计数失败（Redis 不可用?），本次放行: {}", e.toString());
            return true;
        }

        if (count > requestsPerMinute) {
            writeTooManyRequests(response);
            return false;
        }
        return true;
    }

    /** 已登录按用户维度，未登录按 IP（登录/注册这类接口也能被保护）。 */
    private String resolveIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return "u:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
        } catch (Exception e) {
            log.warn("写出 429 响应失败: {}", e.toString());
        }
    }
}
