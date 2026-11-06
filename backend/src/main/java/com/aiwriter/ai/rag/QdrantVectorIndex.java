package com.aiwriter.ai.rag;

import com.aiwriter.model.FrameworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库实现（REST，不额外引入 gRPC 依赖）。
 *
 * <p>为什么是 Qdrant：单容器、概念少（collection / point / payload）、REST 直接可调，
 * 适合百到百万级；真到亿级再换 Milvus。换库只需实现 {@link VectorIndex}。
 *
 * <p>几个设计取舍：
 * <ul>
 *   <li><b>过滤条件放进检索请求</b>（frameworkId + 未弃用），而不是取回来再筛——
 *       否则被过滤的点仍然参与相似度计算，白算还可能挤掉真正相关的；</li>
 *   <li><b>失败降级</b>：检索抛异常时由 {@link ElementRanker} 退回内存余弦，
 *       向量库不可用不能让写作断掉（和 Redis 限流那套一致的可用性策略）；</li>
 *   <li><b>写入是 best-effort</b>：元素增改后异步同步，失败只记日志，不回滚业务事务。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ai.rag.vector-store", havingValue = "qdrant")
public class QdrantVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorIndex.class);

    private final WebClient client;
    private final String collection;
    private final EmbeddingModel embeddingModel;
    private final int dim;
    private volatile boolean collectionReady = false;

    public QdrantVectorIndex(WebClient.Builder builder,
                             EmbeddingModel embeddingModel,
                             @Value("${ai.rag.qdrant.url:http://localhost:6333}") String url,
                             @Value("${ai.rag.qdrant.collection:aiwriter_elements}") String collection,
                             @Value("${ai.rag.qdrant.dim:512}") int dim) {
        this.client = builder.baseUrl(url).build();
        this.collection = collection;
        this.embeddingModel = embeddingModel;
        this.dim = dim;
    }

    @Override
    public List<String> search(Long frameworkId, String query, int limit) {
        ensureCollection();
        float[] vec = embeddingModel.embed(query);
        Map<String, Object> body = Map.of(
                "vector", toList(vec),
                "limit", limit,
                "with_payload", true,
                "filter", Map.of("must", List.of(
                        Map.of("key", "framework_id", "match", Map.of("value", frameworkId)),
                        Map.of("key", "is_deprecated", "match", Map.of("value", false))
                ))
        );
        Map<?, ?> resp = client.post()
                .uri("/collections/{c}/points/search", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
        List<String> ids = new ArrayList<>();
        if (resp != null && resp.get("result") instanceof List<?> hits) {
            for (Object h : hits) {
                if (h instanceof Map<?, ?> hit && hit.get("payload") instanceof Map<?, ?> p) {
                    Object id = p.get("element_id");
                    if (id != null) ids.add(id.toString());
                }
            }
        }
        return ids;
    }

    @Override
    public void upsert(FrameworkElement el) {
        try {
            ensureCollection();
            float[] vec = embeddingModel.embed(toText(el));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("element_id", el.getId());
            payload.put("framework_id", el.getFramework().getId());
            payload.put("type", el.getType());
            payload.put("name", el.getName());
            payload.put("is_deprecated", Boolean.TRUE.equals(el.getIsDeprecated()));
            Map<String, Object> point = Map.of(
                    "id", el.getId(),
                    "vector", toList(vec),
                    "payload", payload
            );
            client.put()
                    .uri("/collections/{c}/points?wait=true", collection)
                    .bodyValue(Map.of("points", List.of(point)))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (RuntimeException e) {
            // 索引失败不影响业务写入，交给 reindex 兜底
            log.warn("Qdrant 写入失败（不影响业务）elementId={}: {}", el.getId(), e.toString());
        }
    }

    @Override
    public void delete(String elementId) {
        try {
            client.post()
                    .uri("/collections/{c}/points/delete?wait=true", collection)
                    .bodyValue(Map.of("points", List.of(elementId)))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (RuntimeException e) {
            log.warn("Qdrant 删除失败（不影响业务）elementId={}: {}", elementId, e.toString());
        }
    }

    @Override
    public int reindex(Long frameworkId, List<FrameworkElement> elements) {
        ensureCollection();
        int n = 0;
        for (FrameworkElement el : elements) {
            upsert(el);
            n++;
        }
        log.info("框架 {} 的向量索引重建完成，共 {} 条", frameworkId, n);
        return n;
    }

    /** 懒建集合：512 维 + Cosine，和 HashingEmbeddingModel 的维度对齐。 */
    private void ensureCollection() {
        if (collectionReady) return;
        synchronized (this) {
            if (collectionReady) return;
            try {
                client.put()
                        .uri("/collections/{c}", collection)
                        .bodyValue(Map.of("vectors", Map.of("size", dim, "distance", "Cosine")))
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(5));
            } catch (RuntimeException e) {
                // 集合已存在会报错，忽略即可
                log.debug("集合 {} 可能已存在: {}", collection, e.toString());
            }
            collectionReady = true;
        }
    }

    private static String toText(FrameworkElement el) {
        return el.getType() + " " + el.getName() + " " + el.getContent();
    }

    private static List<Double> toList(float[] vec) {
        List<Double> out = new ArrayList<>(vec.length);
        for (float v : vec) out.add((double) v);
        return out;
    }
}

