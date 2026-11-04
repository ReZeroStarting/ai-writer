package com.aiwriter.ai.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地哈希向量：把字符 n-gram 用带符号哈希映射到固定维度并 L2 归一化。
 *
 * <p>它不是语义向量，**只是"字面相近"的向量**：同一个词、同一类词的字符片段会撞到相近的桶里，
 * 所以对"设定元素 ↔ 当前章节"这种同领域短文本的粗筛够用，且完全离线、确定性、
 * 零外部依赖——CI 里不需要 key 也能跑。
 *
 * <p>什么时候该换掉它：需要跨表述的语义匹配（"剑客" ↔ "用剑的人"）时，
 * 把 {@code ai.embedding.provider} 切成 {@code openai} 走真 embedding。
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.provider", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingModel implements EmbeddingModel {

    /** 维度取 512：几百条设定元素的召回，桶冲突已足够低。 */
    private static final int DIM = 512;

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) return vec;
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        for (int n = 2; n <= 3; n++) {
            for (int i = 0; i + n <= normalized.length(); i++) {
                String gram = normalized.substring(i, i + n);
                int hash = gram.hashCode();
                int idx = Math.floorMod(hash, DIM);
                // 带符号哈希：降低不同 gram 撞同一桶时的偏置
                vec[idx] += (hash & 1) == 0 ? 1f : -1f;
            }
        }
        return normalize(vec);
    }

    @Override
    public String name() {
        return "hashing-ngram";
    }

    private static float[] normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) sum += v * v;
        if (sum == 0) return vec;
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        return vec;
    }
}
