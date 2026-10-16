package com.aiwriter.ai;

import com.aiwriter.exception.UpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private final WebClient webClient;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeService(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.base-url}") String baseUrl,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.temperature}") double temperature,
            @Value("${anthropic.max-tokens}") int maxTokens) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    public Flux<String> stream(String systemPrompt, String userPrompt) {
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, true);
        return webClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank())
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6))
                .filter(data -> !"[DONE]".equals(data.trim()))
                .map(this::extractDeltaText)
                .filter(text -> text != null && !text.isEmpty())
                // 上游（Anthropic）报错时，不要以 500 中断连接——把可读原因作为一条流式消息推给前端，
                // 用户才能知道是「API Key 未配置/无效」还是「额度/网络」问题，而不是笼统的"服务器内部错误"。
                .onErrorResume(err -> {
                    log.error("AI 上游调用失败: {}", err.getMessage());
                    return Flux.just(describeUpstreamError(err));
                });
    }

    /** 把上游异常翻译成用户可读的一句话说明。 */
    private String describeUpstreamError(Throwable err) {
        String msg = String.valueOf(err.getMessage());
        if (msg.contains("401") || msg.contains("403")) {
            return "【AI 服务未授权】请检查后端配置的 ANTHROPIC_API_KEY 是否有效。";
        }
        if (msg.contains("429")) {
            return "【AI 服务限流】请求过于频繁或额度不足，请稍后再试。";
        }
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
            return "【AI 服务暂时不可用】上游返回错误，请稍后再试。";
        }
        if (msg.contains("UnknownHost") || msg.contains("Connection") || msg.contains("timeout")) {
            return "【网络异常】无法连接 AI 服务，请检查网络或代理设置。";
        }
        return "【AI 调用失败】" + msg;
    }

    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, false);
        String response;
        try {
            response = webClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception err) {
            // 与 stream() 保持一致：上游失败时给出可读原因并映射为 502，
            // 而不是把 NPE/WebClientException 直接抛成 500「服务器内部错误」。
            log.error("AI 上游调用失败(complete): {}", err.getMessage());
            throw new UpstreamException(describeUpstreamError(err));
        }
        return extractContentText(response);
    }

    private Map<String, Object> buildRequestBody(String system, String user, boolean stream) {
        return Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)),
                "stream", stream
        );
    }

    private String extractDeltaText(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node.has("type") && "content_block_delta".equals(node.get("type").asText())) {
                JsonNode delta = node.get("delta");
                if (delta != null && delta.has("text")) {
                    return delta.get("text").asText();
                }
            }
        } catch (Exception e) {
            log.warn("解析SSE delta失败: {}", json, e);
        }
        return "";
    }

    private String extractContentText(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode content = root.get("content");
            if (content != null && content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : content) {
                    JsonNode text = block.get("text");
                    if (text != null) sb.append(text.asText());
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("解析Claude响应失败: {}", json, e);
        }
        return "";
    }
}
