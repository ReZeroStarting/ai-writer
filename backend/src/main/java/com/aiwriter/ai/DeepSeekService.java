package com.aiwriter.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 服务端默认的 DeepSeek 上游（当 <code>ai.provider=deepseek</code> 时启用）。
 *
 * <p>协议本身与其它 OpenAI 兼容厂商完全一致，逻辑都在
 * {@link OpenAiCompatibleService} 里；这个类只负责把 Spring 配置注入进去。
 * 用户自己在「AI 设置」里选的厂商，则由 {@link AiProviderResolver} 按预设直接构造。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "deepseek")
public class DeepSeekService extends OpenAiCompatibleService {

    public DeepSeekService(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.model}") String model,
            @Value("${deepseek.temperature}") double temperature,
            @Value("${deepseek.max-tokens}") int maxTokens) {
        super("deepseek", "服务端 DEEPSEEK_API_KEY", apiKey, baseUrl, model, temperature, maxTokens);
    }
}
