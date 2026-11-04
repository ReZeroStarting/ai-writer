package com.aiwriter.ai.engine;

import com.aiwriter.exception.UpstreamException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallGuardTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    /** 超时 5 秒、重试 2 次、连续失败 2 次熔断。 */
    private AiCallGuard guard() {
        return new AiCallGuard(meters, 5, 2, 2, 30);
    }

    @Test
    @DisplayName("成功调用返回值，并记录 ok 指标")
    void blockingSuccessRecordsMetric() {
        String value = guard().blocking("deepseek", () -> "hello");

        assertThat(value).isEqualTo("hello");
        assertThat(meters.counter("ai.calls", "provider", "deepseek", "outcome", "ok").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("第一次失败会重试，第二次成功则整体成功")
    void blockingRetriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String value = guard().blocking("claude", () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("模拟上游抖动");
            return "ok";
        });

        assertThat(value).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(meters.counter("ai.calls", "provider", "claude", "outcome", "retry").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("连续失败达到阈值后熔断，后续调用不再打到上游")
    void breakerOpensAfterThreshold() {
        AiCallGuard guard = guard();
        AtomicInteger attempts = new AtomicInteger();
        Runnable alwaysFail = () -> {
            attempts.incrementAndGet();
            throw new UpstreamException("上游 500");
        };

        // 第一次调用：重试耗尽期间失败次数达到阈值 -> 熔断打开，最后一次尝试被拒绝
        assertThatThrownBy(() -> guard.blocking("openai", () -> {
            alwaysFail.run();
            return "never";
        })).isInstanceOf(RuntimeException.class);

        int attemptsAfterFirstCall = attempts.get();

        // 第二次调用：熔断中，不应该再触达上游
        assertThatThrownBy(() -> guard.blocking("openai", () -> {
            attempts.incrementAndGet();
            return "never";
        })).isInstanceOf(UpstreamException.class)
                .hasMessageContaining("冷却");

        assertThat(attempts.get()).isEqualTo(attemptsAfterFirstCall);
        assertThat(guard.isOpen("openai")).isTrue();
    }

    @Test
    @DisplayName("流式调用超时会抛出，不会被无限挂起")
    void fluxTimesOut() {
        AiCallGuard guard = new AiCallGuard(meters, 1, 0, 5, 30);

        assertThatThrownBy(() -> guard.flux("slow", () -> reactor.core.publisher.Flux.never())
                        .blockLast())
                .isInstanceOf(RuntimeException.class);
    }
}
