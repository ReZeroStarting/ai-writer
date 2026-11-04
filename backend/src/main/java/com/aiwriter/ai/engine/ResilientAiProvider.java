package com.aiwriter.ai.engine;

import com.aiwriter.ai.AiProvider;
import reactor.core.publisher.Flux;

/**
 * 给任意 {@link AiProvider} 套上韧性层（超时 / 重试 / 熔断 / 指标）。
 *
 * <p>用装饰器而不是在每个实现里各写一遍，是因为「怎么能失败、失败了怎么处理」
 * 与「用哪家协议」是两件正交的事：新增一家上游时不需要再考虑重试和熔断。
 */
public class ResilientAiProvider implements AiProvider {

    private final AiProvider delegate;
    private final AiCallGuard guard;

    public ResilientAiProvider(AiProvider delegate, AiCallGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        return guard.flux(delegate.providerName(), () -> delegate.stream(systemPrompt, userPrompt));
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return guard.blocking(delegate.providerName(), () -> delegate.complete(systemPrompt, userPrompt));
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }
}
