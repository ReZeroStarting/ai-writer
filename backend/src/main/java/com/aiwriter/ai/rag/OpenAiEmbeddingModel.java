package com.aiwriter.ai.rag;

import com.aiwriter.exception.UpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * 走 OpenAI 兼容 {@code /embeddings} 的真 embedding（SiliconFlow / OpenAI / 通义等均可）。
 *
 * <p>只有显式配置 {@code ai.embedding.provider=openai} 时才启用：
 * 默认用本地哈希向量，避免"没配 key 就跑不起来"。
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingModel implements EmbeddingModel {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiEmbeddingModel(
            @Value("${ai.embedding.base-url}") String baseUrl,
            @Value("${ai.embedding.api-key:}") String apiKey,
            @Value("${ai.embedding.model:text-embedding-3-small}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public float[] embed(String text) {
        try {
            String response = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("model", model, "input", text == null ? "" : text))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
            JsonNode node = mapper.readTree(response);
            JsonNode embedding = node.path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new UpstreamException("embedding 返回为空");
            }
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) vec[i] = (float) embedding.get(i).asDouble();
            return vec;
        } catch (UpstreamException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamException("embedding 调用失败: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "openai-compatible:" + model;
    }
}
