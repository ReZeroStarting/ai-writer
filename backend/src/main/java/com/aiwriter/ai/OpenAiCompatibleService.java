package com.aiwriter.ai;

import com.aiwriter.exception.UpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 通用「OpenAI 兼容」上游客户端。
 *
 * <p>市面上绝大多数厂商都提供 <code>POST {baseUrl}/chat/completions</code>、
 * <code>Authorization: Bearer</code>、<code>choices[].delta.content</code> 这一套：
 * OpenAI、DeepSeek、Kimi、智谱、通义、Gemini(兼容入口)、Groq、OpenRouter、
 * 硅基流动、本地 Ollama……差异只在 <b>baseUrl</b> 和 <b>模型名</b>。
 *
 * <p>所以这里只保留一份实现，用构造参数区分厂商；只有 Anthropic 的 Messages 协议
 * 不同，单独由 {@link ClaudeService} 处理。
 */
public class OpenAiCompatibleService implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleService.class);

    private final String name;
    private final String keyHint;
    private final WebClient webClient;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleService(String name, String keyHint, String apiKey, String baseUrl,
                                   String model, double temperature, int maxTokens) {
        this.name = name;
        this.keyHint = keyHint;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(stripTrailingSlash(baseUrl))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public String providerName() {
        return name;
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
                // WebClient 在 text/event-stream 下可能已剥掉 "data: " 前缀，也可能保留
                .map(SseLines::stripDataPrefix)
                .filter(SseLines::isPayload)
                .map(this::extractDeltaText)
                .filter(text -> text != null && !text.isEmpty())
                // 上游失败不中断连接，把可读原因作为一条流式消息推给前端
                .onErrorResume(err -> Flux.just(UpstreamErrors.describe(err, keyHint, name)));
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
            throw new UpstreamException(UpstreamErrors.describe(err, keyHint, name));
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
                // 有些兼容实现把增量放在 message 里（非流式格式混用），兜一下
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.hasNonNull("content")) {
                    return message.get("content").asText();
                }
            }
        } catch (Exception e) {
            log.warn("解析 {} SSE 分片失败: {}", name, json, e);
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
                // 少数兼容实现直接给 text
                JsonNode text = choices.get(0).get("text");
                if (text != null) return text.asText();
            }
        } catch (Exception e) {
            log.error("解析 {} 响应失败: {}", name, json, e);
        }
        return "";
    }
}
