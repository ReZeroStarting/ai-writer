package com.aiwriter.ai.rag;

import com.aiwriter.model.Framework;
import com.aiwriter.model.FrameworkElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElementRankerTest {

    /** 无向量库（默认内存路径）。 */
    private static org.springframework.beans.factory.ObjectProvider<VectorIndex> noIndex() {
        return org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    }

    private final ElementRanker ranker = new ElementRanker(new HashingEmbeddingModel(), noIndex(), 2, true);

    private FrameworkElement element(String id, String type, String name, String content) {
        return FrameworkElement.builder().id(id).version(1).framework(Framework.builder().id(1L).build())
                .type(type).name(name).content(content).isDeprecated(false).build();
    }

    private List<FrameworkElement> elements() {
        List<FrameworkElement> list = new ArrayList<>();
        list.add(element("e1", "character", "张三", "主角，25 岁剑客，左手使剑"));
        list.add(element("e2", "location", "长安城", "唐朝都城，城门由禁军守卫"));
        list.add(element("e3", "item", "青锋剑", "张三的佩剑，剑身泛青"));
        list.add(element("e4", "organization", "锦衣卫", "负责京畿缉捕"));
        list.add(element("e5", "world", "灵气复苏", "百年前天地异变"));
        return list;
    }

    @Test
    @DisplayName("按与查询的相似度取 top-K，最相关的排在最前")
    void ranksByRelevance() {
        List<FrameworkElement> top = ranker.rank(elements(), "长安城 城门 禁军");

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getId()).isEqualTo("e2");
    }

    @Test
    @DisplayName("元素数量不超过 K 时仍全部返回，但按相关性排序（不再直接返回原顺序）")
    void ranksEvenWhenUnderK() {
        List<FrameworkElement> small = List.of(
                element("a", "character", "甲", "一"),
                element("b", "character", "乙", "二"));

        List<FrameworkElement> out = ranker.rank(small, "一");

        assertThat(out).hasSize(2);
        assertThat(out).containsExactlyInAnyOrderElementsOf(small);
        // 查询是"一"，含"一"的 a 应排在前面；这条断言守住"数量少也要排序"的语义
        assertThat(out.get(0).getId()).isEqualTo("a");
    }

    @Test
    @DisplayName("查询为空时保持原顺序（不改变既有行为）")
    void keepsOrderWhenQueryBlank() {
        List<FrameworkElement> all = elements();

        assertThat(ranker.rank(all, "   ")).isSameAs(all);
        assertThat(ranker.rank(all, null)).isSameAs(all);
    }

    @Test
    @DisplayName("空列表返回空，不抛异常")
    void emptyInput() {
        assertThat(ranker.rank(List.of(), "x")).isEmpty();
        assertThat(ranker.rank(null, "x")).isEmpty();
    }
}


