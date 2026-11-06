package com.aiwriter.ai.rag;

import com.aiwriter.model.FrameworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 设定元素的**相关性排序**（RAG 的 R 层）。
 *
 * <p>解决的问题：框架模式下设定可能有几十上百条，全塞进 prompt 会超预算，
 * 而原来超预算就是"直接截断"——被截掉的往往正是本章要用的那条。
 * 这里改成按与当前正文的相似度取 top-K，先保住最相关的。
 *
 * <p><b>两路召回 + RRF 融合（hybrid）</b>：
 * <ul>
 *   <li>向量路：走向量库（配了才走）或内存余弦——擅长"语义相近"；</li>
 *   <li>关键词路：字符 bigram 重合度——擅长"专有名词精确命中"（人名、地名、剑名）；</li>
 *   <li>融合用 RRF：只看名次不看分数，两路分数量纲不同也能合。</li>
 * </ul>
 *
 * <p>向量库不可用时自动退回内存实现——检索是优化，不能让写作断掉。
 */
@Component
public class ElementRanker {

    private static final Logger log = LoggerFactory.getLogger(ElementRanker.class);

    /** RRF 常数：经验值 60，压制头部名次的绝对优势，让两路都能贡献。 */
    private static final int RRF_K = 60;

    private final EmbeddingModel embeddingModel;
    private final VectorIndex vectorIndex;
    private final int topK;
    private final boolean hybridEnabled;

    public ElementRanker(EmbeddingModel embeddingModel,
                         ObjectProvider<VectorIndex> vectorIndex,
                         @Value("${ai.rag.top-k:12}") int topK,
                         @Value("${ai.rag.hybrid:true}") boolean hybridEnabled) {
        this.embeddingModel = embeddingModel;
        this.vectorIndex = vectorIndex.getIfAvailable();
        this.topK = Math.max(1, topK);
        this.hybridEnabled = hybridEnabled;
    }

    /**
     * 返回按相关性排序的前 K 条；查询为空时原样返回（不改变既有行为）。
     * 向量化/检索失败时**降级**（退内存余弦，再不行退原顺序），不能让检索把写作链路弄挂。
     */
    public List<FrameworkElement> rank(List<FrameworkElement> elements, String query) {
        if (elements == null || elements.isEmpty()) return List.of();
        if (query == null || query.isBlank()) return elements;

        List<FrameworkElement> vectorOrdered = vectorOrdered(elements, query);
        if (!hybridEnabled) return vectorOrdered.stream().limit(topK).toList();

        List<FrameworkElement> keywordOrdered = keywordOrdered(elements, query);
        return fuse(vectorOrdered, keywordOrdered).stream().limit(topK).toList();
    }

    /** 向量路：优先向量库，失败/为空则退回内存余弦，再失败退原顺序。 */
    private List<FrameworkElement> vectorOrdered(List<FrameworkElement> elements, String query) {
        if (vectorIndex != null) {
            try {
                Long frameworkId = elements.get(0).getFramework().getId();
                List<String> ids = vectorIndex.search(frameworkId, query, Math.max(topK, elements.size()));
                if (!ids.isEmpty()) {
                    Map<String, FrameworkElement> byId = new HashMap<>();
                    for (FrameworkElement el : elements) byId.put(el.getId(), el);
                    List<FrameworkElement> ordered = ids.stream()
                            .map(byId::get).filter(Objects::nonNull).distinct().toList();
                    if (!ordered.isEmpty()) return ordered;
                }
            } catch (RuntimeException e) {
                log.warn("向量库检索失败，退回内存余弦: {}", e.toString());
            }
        }
        try {
            float[] queryVec = embeddingModel.embed(query);
            return elements.stream()
                    .map(el -> Map.entry(el, cosine(queryVec, embeddingModel.embed(toText(el)))))
                    .sorted(Comparator.comparingDouble((Map.Entry<FrameworkElement, Float> e) -> e.getValue()).reversed())
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("设定元素向量化失败，降级为原顺序: {}", e.toString());
            return elements;
        }
    }

    /** 关键词路：查询与元素文本的字符 bigram 重合数，降序；全不重合时返回空表。 */
    private List<FrameworkElement> keywordOrdered(List<FrameworkElement> elements, String query) {
        Set<String> qg = bigrams(query);
        if (qg.isEmpty()) return List.of();
        return elements.stream()
                .map(el -> Map.entry(el, (double) overlap(qg, bigrams(toText(el)))))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingDouble((Map.Entry<FrameworkElement, Double> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * RRF 融合：score(d) = Σ 1 / (K + rank_i(d))，rank 从 1 开始；没出现在某一路的记 0 分。
     * 两路都没覆盖的元素垫后（不丢），保证调用方拿到的还是完整集合的前 K。
     */
    private List<FrameworkElement> fuse(List<FrameworkElement> a, List<FrameworkElement> b) {
        Map<String, Double> score = new LinkedHashMap<>();
        Map<String, FrameworkElement> byId = new LinkedHashMap<>();
        for (FrameworkElement el : a) byId.putIfAbsent(el.getId(), el);
        for (FrameworkElement el : b) byId.putIfAbsent(el.getId(), el);

        for (int i = 0; i < a.size(); i++) {
            score.merge(a.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < b.size(); i++) {
            score.merge(b.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<FrameworkElement> fused = new ArrayList<>(byId.values());
        fused.sort(Comparator.comparingDouble((FrameworkElement el) -> score.getOrDefault(el.getId(), 0.0)).reversed());
        return fused;
    }

    private static Set<String> bigrams(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        String s = text.replaceAll("\\s+", "");
        for (int i = 0; i + 1 < s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }

    private static int overlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String g : a) if (b.contains(g)) n++;
        return n;
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
