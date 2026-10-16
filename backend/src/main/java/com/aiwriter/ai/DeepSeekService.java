package com.aiwriter.ai;

import com.aiwriter.exception.UpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 实现，走其**原生 OpenAI 兼容协议**。
 *
 * <p>当 <code>ai.provider=deepseek</code> 时启用。
 *
 * <p>与 Anthropic 的差异（这是本类独立存在的原因）：
 * <ul>
 *   <li>端点：<code>/chat/completions</code>（不是 <code>/v1/messages</code>）</li>
 *   <li>鉴权：<code>Authorization: Bearer &lt;key&gt;</code>（不是 <code>x-api-key</code>）</li>
 *   <li>system 提示词：作为 messages 数组的第一条，而不是顶层 <code>system</code> 字段</li>
 *   <li>流式增量：<code>choices[0].delta.content</code>（不是 <code>content_block_delta</code>）</li>
 * </ul>
 *
 * <p>另注：DeepSeek 也提供 Anthropic 兼容端点
 * （<code>https://api.deepseek.com/anthropic</code>），若不启用本类，
 * 也可直接把 anthropic.base-url 指向该地址并改用 DeepSeek 模型名，
 * 但那条路依赖兼容层，本类走原生协议更稳妥。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "deepseek")
public class DeepSeekService implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);
    private static final String KEY_NAME = "DEEPSEEK_API_KEY";

    private final WebClient webClient;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekService(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.model}") String model,
            @Value("${deepseek.temperature}") double temperature,
            @Value("${deepseek.max-tokens}") int maxTokens) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, true);
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // 注意：WebClient 在 text/event-stream 下会先用 SSE 解码器处理，
                // 此时拿到的 String 可能已经剥掉了 "data: " 前缀；但若走了普通文本解码，
                // 前缀仍在。两种形式都要兼容，不能假设只有一种。
                .map(SseLines::stripDataPrefix)
                .filter(SseLines::isPayload)
                .map(this::extractDeltaText)
                .filter(text -> text != null && !text.isEmpty())
                // 与 ClaudeService 保持一致的约定：上游失败不中断连接，
                // 把可读原因作为一条流式消息推给前端。
                .onErrorResume(err -> Flux.just(UpstreamErrors.describe(err, KEY_NAME, providerName())));
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, false);
        String response;
        try {
            response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception err) {
            throw new UpstreamException(UpstreamErrors.describe(err, KEY_NAME, providerName()));
        }
        return extractContentText(response);
    }

    private Map<String, Object> buildRequestBody(String system, String user, boolean stream) {
        // OpenAI 协议把 system 放进 messages，而不是放在顶层
        return Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                ),
                "stream", stream
        );
    }

    /** 解析流式分片：<code>{"choices":[{"delta":{"content":"..."}}]}</code> */
    private String extractDeltaText(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.hasNonNull("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception e) {
            log.warn("解析 DeepSeek SSE 分片失败: {}", json, e);
        }
        return "";
    }

    /** 解析非流式响应：<code>{"choices":[{"message":{"content":"..."}}]}</code> */
    private String extractContentText(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.hasNonNull("content")) {
                    return message.get("content").asText();
                }
            }
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败: {}", json, e);
        }
        return "";
    }
}
