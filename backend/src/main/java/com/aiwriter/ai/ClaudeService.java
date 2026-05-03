package com.aiwriter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {
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
                .filter(text -> text != null && !text.isEmpty());
    }

    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, false);
        String response = webClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
        }
        return "";
    }
}
