package com.aiwriter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitInterceptorTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private RateLimitInterceptor interceptor(boolean enabled, int rpm) {
        when(redis.opsForValue()).thenReturn(valueOps);
        return new RateLimitInterceptor(redis, enabled, rpm);
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    @Test
    @DisplayName("未超限时放行，并对首次计数设置过期")
    void allowsUnderLimit() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse res = response();

        boolean allowed = interceptor(true, 60)
                .preHandle(new MockHttpServletRequest(), res, new Object());

        assertThat(allowed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
        verify(redis).expire(anyString(), any());
    }

    @Test
    @DisplayName("超过阈值返回 429，并给出可读提示")
    void blocksOverLimit() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(61L);
        MockHttpServletResponse res = response();

        boolean allowed = interceptor(true, 60)
                .preHandle(new MockHttpServletRequest(), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("请求过于频繁");
    }

    @Test
    @DisplayName("Redis 故障时放行——限流不该成为新的故障点")
    void failsOpenWhenRedisDown() {
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        MockHttpServletResponse res = response();

        boolean allowed = interceptor(true, 60)
                .preHandle(new MockHttpServletRequest(), res, new Object());

        assertThat(allowed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("关闭限流时完全不碰 Redis")
    void disabledSkipsRedis() {
        boolean allowed = interceptor(false, 60)
                .preHandle(new MockHttpServletRequest(), response(), new Object());

        assertThat(allowed).isTrue();
        verifyNoInteractions(valueOps);
    }
}
