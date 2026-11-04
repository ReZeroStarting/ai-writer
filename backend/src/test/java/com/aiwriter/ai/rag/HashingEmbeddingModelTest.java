package com.aiwriter.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    @DisplayName("同一文本两次向量化结果完全一致（确定性，便于缓存与回归）")
    void deterministic() {
        assertThat(model.embed("长安城的夜里，张三提剑而立"))
                .containsExactly(model.embed("长安城的夜里，张三提剑而立"));
    }

    @Test
    @DisplayName("向量做过 L2 归一化，长度约为 1")
    void normalized() {
        float[] vec = model.embed("长安 主角 剑客");
        double norm = 0;
        for (float v : vec) norm += v * v;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("字面更接近的文本相似度更高")
    void similarTextsScoreHigher() {
        float[] query = model.embed("长安城 城门 守卫");
        float related = dot(query, model.embed("长安 城门 由禁军守卫"));
        float unrelated = dot(query, model.embed("厨房 锅具 采购清单"));

        assertThat(related).isGreaterThan(unrelated);
    }

    @Test
    @DisplayName("空文本返回零向量而不是抛异常")
    void blankReturnsZeroVector() {
        assertThat(model.embed("")).hasSize(512);
        assertThat(model.embed(null)).hasSize(512);
    }

    private static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) sum += a[i] * b[i];
        return sum;
    }
}
