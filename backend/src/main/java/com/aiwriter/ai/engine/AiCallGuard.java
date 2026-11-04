package com.aiwriter.ai.engine;

import com.aiwriter.exception.UpstreamException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * AI 调用韧性层：**超时 + 重试 + 熔断 + 指标**。
 *
 * <p>为什么需要它：上游大模型不是本服务的一部分，它可能慢、可能 429、可能整段时间不可用。
 * 没有这一层时，一个卡住的上游会把 Tomcat 线程占满，整个站点被拖死。
 *
 * <p>三条规则：
 * <ul>
 *   <li><b>超时</b>：单次调用超过 {@code ai.engine.timeout-seconds} 直接失败，不无限等；</li>
 *   <li><b>重试</b>：只对「还没吐出任何内容」的失败重试——流已经开始了再重试会重复文本；</li>
 *   <li><b>熔断</b>：连续失败达到阈值后打开断路器，直接快速失败，给上游恢复时间。</li>
 * </ul>
 *
 * <p>指标（Prometheus 可抓）：{@code ai_calls_total{provider,outcome}}、
 * {@code ai_call_duration_seconds{provider}}。
 */
@Component
public class AiCallGuard {

    private static final Logger log = LoggerFactory.getLogger(AiCallGuard.class);

    private final MeterRegistry meters;
    private final Duration timeout;
    private final int maxRetries;
    private final int breakerThreshold;
    private final Duration breakerOpenFor;
    private final Map<String, Breaker> breakers = new ConcurrentHashMap<>();

    public AiCallGuard(
            MeterRegistry meters,
            @Value("${ai.engine.timeout-seconds:90}") long timeoutSeconds,
            @Value("${ai.engine.max-retries:2}") int maxRetries,
            @Value("${ai.engine.breaker-threshold:5}") int breakerThreshold,
            @Value("${ai.engine.breaker-open-seconds:30}") long breakerOpenSeconds) {
        this.meters = meters;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = Math.max(0, maxRetries);
        this.breakerThreshold = Math.max(1, breakerThreshold);
        this.breakerOpenFor = Duration.ofSeconds(breakerOpenSeconds);
    }

    /** 流式调用：超时 + 首个元素前可重试 + 熔断 + 指标。 */
    public <T> Flux<T> flux(String provider, Supplier<Flux<T>> call) {
        return Flux.defer(() -> {
            Breaker breaker = breaker(provider);
            if (!breaker.allow()) {
                count(provider, "rejected");
                return Flux.error(new UpstreamException("上游连续失败，已进入冷却，请稍后再试"));
            }
            Timer.Sample sample = Timer.start(meters);
            AtomicBoolean emitted = new AtomicBoolean(false);
            return call.get()
                    .doOnNext(ignored -> emitted.set(true))
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(300))
                            .filter(err -> !emitted.get())
                            .doBeforeRetry(rs -> count(provider, "retry"))
                            .onRetryExhaustedThrow((spec, sig) -> sig.failure()))
                    .doOnComplete(() -> {
                        breaker.success();
                        count(provider, "ok");
                        sample.stop(timer(provider));
                    })
                    .doOnError(err -> {
                        breaker.failure();
                        count(provider, "error");
                        sample.stop(timer(provider));
                        log.warn("AI 流式调用失败 provider={} : {}", provider, err.toString());
                    });
        });
    }

    /** 单次调用（Mono）：超时 + 熔断 + 指标。 */
    public <T> Mono<T> mono(String provider, Supplier<Mono<T>> call) {
        return Mono.defer(() -> {
            Breaker breaker = breaker(provider);
            if (!breaker.allow()) {
                count(provider, "rejected");
                return Mono.error(new UpstreamException("上游连续失败，已进入冷却，请稍后再试"));
            }
            Timer.Sample sample = Timer.start(meters);
            return call.get()
                    .timeout(timeout)
                    .doOnSuccess(v -> {
                        breaker.success();
                        count(provider, "ok");
                        sample.stop(timer(provider));
                    })
                    .doOnError(err -> {
                        breaker.failure();
                        count(provider, "error");
                        sample.stop(timer(provider));
                    });
        });
    }

    /** 阻塞调用：重试 + 熔断 + 指标（用于 complete() 这类一次性取结果的场景）。 */
    public <T> T blocking(String provider, Supplier<T> call) {
        Breaker breaker = breaker(provider);
        Timer.Sample sample = Timer.start(meters);
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (!breaker.allow()) {
                count(provider, "rejected");
                throw new UpstreamException("上游连续失败，已进入冷却，请稍后再试");
            }
            try {
                T value = call.get();
                breaker.success();
                count(provider, "ok");
                sample.stop(timer(provider));
                return value;
            } catch (RuntimeException err) {
                last = err;
                breaker.failure();
                count(provider, "error");
                if (attempt < maxRetries) {
                    count(provider, "retry");
                    sleep(300L * (attempt + 1));
                }
            }
        }
        sample.stop(timer(provider));
        throw last != null ? last : new UpstreamException("上游调用失败");
    }

    /** 供健康检查/排查用：当前断路器是否处于冷却中。 */
    public boolean isOpen(String provider) {
        return !breaker(provider).allow();
    }

    private Breaker breaker(String provider) {
        return breakers.computeIfAbsent(provider == null ? "unknown" : provider,
                key -> new Breaker(breakerThreshold, breakerOpenFor.toMillis()));
    }

    private void count(String provider, String outcome) {
        meters.counter("ai.calls", "provider", provider == null ? "unknown" : provider, "outcome", outcome)
                .increment();
    }

    private Timer timer(String provider) {
        return Timer.builder("ai.call.duration")
                .tag("provider", provider == null ? "unknown" : provider)
                .publishPercentileHistogram()
                .register(meters);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 极简断路器：连续失败 N 次后打开，冷却时间过后尝试恢复。 */
    static final class Breaker {
        private final int threshold;
        private final long openMillis;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private final AtomicLong openUntil = new AtomicLong(0);

        Breaker(int threshold, long openMillis) {
            this.threshold = threshold;
            this.openMillis = openMillis;
        }

        boolean allow() {
            return System.currentTimeMillis() >= openUntil.get();
        }

        void success() {
            consecutiveFailures.set(0);
            openUntil.set(0);
        }

        void failure() {
            if (consecutiveFailures.incrementAndGet() >= threshold) {
                openUntil.set(System.currentTimeMillis() + openMillis);
                consecutiveFailures.set(0);
            }
        }
    }
}
