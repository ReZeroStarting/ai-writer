package com.aiwriter.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TokenStoreTest {

    private static final Long USER_ID = 7L;
    private static final String JTI = "jti-abc";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private TokenStore store() {
        when(redis.opsForValue()).thenReturn(valueOps);
        return new TokenStore(redis);
    }

    @Test
    @DisplayName("登记 refresh 时写入带 TTL 的键")
    void saveWritesKeyWithTtl() {
        TokenStore store = store();

        store.save(USER_ID, JTI, Duration.ofDays(7));

        verify(valueOps).set(eq("auth:refresh:7:jti-abc"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("键存在时校验通过")
    void activeWhenKeyExists() {
        when(redis.hasKey("auth:refresh:7:jti-abc")).thenReturn(true);

        assertThat(store().isActive(USER_ID, JTI)).isTrue();
    }

    @Test
    @DisplayName("键被删（登出/轮换）后校验不通过")
    void inactiveAfterRevoke() {
        when(redis.hasKey("auth:refresh:7:jti-abc")).thenReturn(false);

        assertThat(store().isActive(USER_ID, JTI)).isFalse();
    }

    @Test
    @DisplayName("Redis 故障时降级放行，不把登录链路拖死")
    void failsOpenWhenRedisDown() {
        when(redis.hasKey(any())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store().isActive(USER_ID, JTI)).isTrue();
    }

    @Test
    @DisplayName("jti 为空直接判无效（不查 Redis）")
    void blankJtiIsInvalid() {
        TokenStore store = store();

        assertThat(store.isActive(USER_ID, null)).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("consume：DEL 返回 true 表示这次轮换归我，返回 false 表示已被别人消费")
    void consumeIsAtomic() {
        when(redis.delete(eq("auth:refresh:7:jti-abc"))).thenReturn(Boolean.TRUE, Boolean.FALSE);

        assertThat(store().consume(USER_ID, JTI)).isTrue();
        // 第二次调用时 key 已经没了 —— 并发刷新里只有第一个能拿到 true
        assertThat(store().consume(USER_ID, JTI)).isFalse();
    }

    @Test
    @DisplayName("consume：Redis 故障时降级放行，不把刷新链路锁死")
    void consumeFailsOpen() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store().consume(USER_ID, JTI)).isTrue();
    }
}
