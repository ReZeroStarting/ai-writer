package com.aiwriter.ai.rag;

import com.aiwriter.model.Framework;
import com.aiwriter.model.FrameworkElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ElementRanker 的三种行为：纯向量、混合（向量 + 关键词 RRF）、降级。
 *
 * <p>重点不在排序算法本身，而在**混合与降级**：
 * 关键词能救回向量排错的元素；向量库挂了必须还能排序，不能抛给上层。
 */
class ElementRankerVectorIndexTest {

    private final EmbeddingModel embedding = new HashingEmbeddingModel();

    /** 只实现 ObjectProvider 的四个抽象方法的极简桩。 */
    private static ObjectProvider<VectorIndex> provider(VectorIndex idx) {
        return new ObjectProvider<>() {
            @Override public VectorIndex getObject() { return idx; }
            @Override public VectorIndex getObject(Object... args) { return idx; }
            @Override public VectorIndex getIfAvailable() { return idx; }
            @Override public VectorIndex getIfUnique() { return idx; }
        };
    }

    private FrameworkElement el(String id, String name, Long frameworkId) {
        Framework fw = new Framework();
        fw.setId(frameworkId);
        return FrameworkElement.builder()
                .id(id).version(1).framework(fw)
                .type("character").name(name).content(name + "的内容")
                .isDeprecated(false).build();
    }

    private List<FrameworkElement> three() {
        return List.of(el("a", "青锋", 9L), el("b", "乙", 9L), el("c", "丙", 9L));
    }

    @Test
    @DisplayName("关闭 hybrid：严格按向量库返回的顺序")
    void usesVectorIndexOrder() {
        VectorIndex idx = mock(VectorIndex.class);
        when(idx.search(9L, "剑客", 3)).thenReturn(List.of("c", "a"));
        var ranker = new ElementRanker(embedding, provider(idx), 2, false);

        List<FrameworkElement> out = ranker.rank(three(), "剑客");

        assertThat(out).extracting(FrameworkElement::getId).containsExactly("c", "a");
    }

    @Test
    @DisplayName("混合检索：关键词精确命中能救回向量排错的元素")
    void hybridPromotesKeywordHit() {
        // 向量路故意给错：把"青锋"排最后
        VectorIndex idx = mock(VectorIndex.class);
        when(idx.search(9L, "青锋", 3)).thenReturn(List.of("c", "b", "a"));
        var ranker = new ElementRanker(embedding, provider(idx), 3, true);

        List<FrameworkElement> out = ranker.rank(three(), "青锋");

        // 关键词路只命中 a，RRF 融合后 a 应当排第一
        assertThat(out.get(0).getId()).isEqualTo("a");
    }

    @Test
    @DisplayName("向量库抛异常时退回内存余弦，不向上抛")
    void fallsBackWhenVectorIndexFails() {
        VectorIndex idx = mock(VectorIndex.class);
        when(idx.search(9L, "剑客", 3)).thenThrow(new RuntimeException("qdrant down"));
        var ranker = new ElementRanker(embedding, provider(idx), 2, true);

        List<FrameworkElement> out = ranker.rank(three(), "剑客");

        assertThat(out).hasSize(2);
        assertThat(out).allSatisfy(e -> assertThat(e.getId()).isIn("a", "b", "c"));
    }

    @Test
    @DisplayName("没有 VectorIndex 时走内存余弦（默认行为不变）")
    void memoryPathByDefault() {
        var ranker = new ElementRanker(embedding, provider(null), 2, true);
        List<FrameworkElement> out = ranker.rank(three(), "剑客");
        assertThat(out).hasSize(2);
    }
}
