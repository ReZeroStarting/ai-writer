package com.aiwriter.ai.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageRecorderTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    /** 输入 2 元/百万、输出 8 元/百万。 */
    private TokenUsageRecorder recorder() {
        return new TokenUsageRecorder(meters, 2.0, 8.0);
    }

    @Test
    @DisplayName("真实 usage：分别累计输入/输出 token，并按单价折算成本")
    void recordsExactUsageAndCost() {
        recorder().record("deepseek", new TokenUsage(1_000_000, 500_000, false));

        assertThat(meters.counter("ai.tokens", "provider", "deepseek", "type", "input").count())
                .isEqualTo(1_000_000);
        assertThat(meters.counter("ai.tokens", "provider", "deepseek", "type", "output").count())
                .isEqualTo(500_000);
        // 1M × 2 元 + 0.5M × 8 元 = 6 元 = 6,000,000 微元
        assertThat(meters.counter("ai.cost.micros", "provider", "deepseek").count())
                .isEqualTo(6_000_000);
        assertThat(meters.counter("ai.tokens.estimated", "provider", "deepseek").count())
                .isZero();
    }

    @Test
    @DisplayName("估算值单独计数，避免和真实用量混在一起")
    void marksEstimatedUsage() {
        recorder().record("claude", new TokenUsage(200, 100, true));

        assertThat(meters.counter("ai.tokens.estimated", "provider", "claude").count()).isEqualTo(1);
        assertThat(meters.counter("ai.tokens", "provider", "claude", "type", "input").count()).isEqualTo(200);
    }

    @Test
    @DisplayName("provider 为空时落到 unknown，不抛异常")
    void toleratesNullProvider() {
        recorder().record(null, new TokenUsage(10, 10, false));

        assertThat(meters.counter("ai.tokens", "provider", "unknown", "type", "input").count()).isEqualTo(10);
    }

    @Test
    @DisplayName("用量全为 0 时不产生成本指标")
    void skipsZeroCost() {
        recorder().record("x", new TokenUsage(0, 0, false));

        assertThat(meters.find("ai.cost.micros").counter()).isNull();
    }
}
