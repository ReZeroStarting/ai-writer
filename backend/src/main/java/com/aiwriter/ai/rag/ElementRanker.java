package com.aiwriter.ai.rag;

import com.aiwriter.model.FrameworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 设定元素的**相关性排序**（RAG 的 R 层）。
 *
 * <p>解决的问题：框架模式下设定可能有几十上百条，全塞进 prompt 会超预算，
 * 而原来超预算就是"直接截断"——被截掉的往往正是本章要用的那条。
 * 这里改成按与当前正文的相似度取 top-K，先保住最相关的。
 *
 * <p>为什么不做成向量库：设定元素天然按 frameworkId 分区、数量在百级，
 * 每次请求现算余弦比维护一个索引 + 失效逻辑更不容易出错；
 * 真到万级或需要跨框架检索时，再换 pgvector/Milvus（接口已抽出，替换成本低）。
 */
@Component
public class ElementRanker {

    private static final Logger log = LoggerFactory.getLogger(ElementRanker.class);

    private final EmbeddingModel embeddingModel;
    private final int topK;

    public ElementRanker(EmbeddingModel embeddingModel,
                         @Value("${ai.rag.top-k:12}") int topK) {
        this.embeddingModel = embeddingModel;
        this.topK = Math.max(1, topK);
    }

    /**
     * 返回按相关性排序的前 K 条；数量不超过 K、或查询为空时原样返回（不改变行为）。
     * 向量化失败时**降级为原顺序**，不能让检索把写作链路弄挂。
     */
    public List<FrameworkElement> rank(List<FrameworkElement> elements, String query) {
        if (elements == null || elements.isEmpty()) return List.of();
        if (elements.size() <= topK || query == null || query.isBlank()) return elements;
        try {
            float[] queryVec = embeddingModel.embed(query);
            return elements.stream()
                    .map(el -> Map.entry(el, cosine(queryVec, embeddingModel.embed(toText(el)))))
                    .sorted(Comparator.comparingDouble((Map.Entry<FrameworkElement, Float> e) -> e.getValue()).reversed())
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("设定元素相关性排序失败，降级为原顺序: {}", e.toString());
            return elements;
        }
    }

    /** 参与向量化的文本：类型 + 名称 + 内容（名称权重最高，放最前面）。 */
    private String toText(FrameworkElement el) {
        return el.getType() + " " + el.getName() + " " + (el.getContent() == null ? "" : el.getContent());
    }

    /** 两个已归一化的向量，点积即余弦相似度。 */
    private static float cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        float sum = 0;
        for (int i = 0; i < len; i++) sum += a[i] * b[i];
        return sum;
    }
}
