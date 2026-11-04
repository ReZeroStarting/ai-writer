package com.aiwriter.ai.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * token 用量与成本记账。
 *
 * <p>为什么值得单独做：只统计"调用次数和耗时"回答不了"这个月花了多少钱、哪个功能最烧钱"。
 * 续写是长文本场景，输出 token 是主要成本项——没有这个数，优化方向只能靠猜。
 *
 * <p>指标：
 * <ul>
 *   <li>{@code ai_tokens_total{provider,type=input|output}}</li>
 *   <li>{@code ai_tokens_estimated_total{provider}} —— 上游没回 usage 的调用次数</li>
 *   <li>{@code ai_cost_micros_total{provider}} —— 按配置单价折算的成本（微元）</li>
 * </ul>
 */
@Component
public class TokenUsageRecorder {

    private final MeterRegistry meters;
    private final double inputPricePerMillion;
    private final double outputPricePerMillion;

    public TokenUsageRecorder(
            MeterRegistry meters,
            @Value("${ai.pricing.input-per-million:2.0}") double inputPricePerMillion,
            @Value("${ai.pricing.output-per-million:8.0}") double outputPricePerMillion) {
        this.meters = meters;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public void record(String provider, TokenUsage usage) {
        if (usage == null) return;
        String p = provider == null ? "unknown" : provider;
        if (usage.input() > 0) {
            meters.counter("ai.tokens", "provider", p, "type", "input").increment(usage.input());
        }
        if (usage.output() > 0) {
            meters.counter("ai.tokens", "provider", p, "type", "output").increment(usage.output());
        }
        if (usage.estimated()) {
            meters.counter("ai.tokens.estimated", "provider", p).increment();
        }
        double cost = usage.input() / 1_000_000.0 * inputPricePerMillion
                + usage.output() / 1_000_000.0 * outputPricePerMillion;
        if (cost > 0) {
            meters.counter("ai.cost.micros", "provider", p).increment(Math.round(cost * 1_000_000));
        }
    }
}
