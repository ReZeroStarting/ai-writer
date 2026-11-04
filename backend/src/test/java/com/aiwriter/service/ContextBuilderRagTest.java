package com.aiwriter.service;

import com.aiwriter.ai.rag.ElementRanker;
import com.aiwriter.ai.rag.HashingEmbeddingModel;
import com.aiwriter.model.Framework;
import com.aiwriter.model.FrameworkElement;
import com.aiwriter.repository.ChapterRepository;
import com.aiwriter.repository.FrameworkElementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RAG 介入后的上下文组装：设定超预算时，应当**按相关性保留**而不是从头截断。
 *
 * <p>这是这条链路最值得锁的行为：被截掉的如果是本章要用的那条设定，
 * AI 就会写出和设定矛盾的内容，而那正是"冲突检测"要事后兜的锅。
 */
class ContextBuilderRagTest {

    private final FrameworkElementRepository elementRepo = mock(FrameworkElementRepository.class);
    private final ChapterRepository chapterRepo = mock(ChapterRepository.class);
    private final ElementRanker ranker = new ElementRanker(new HashingEmbeddingModel(), 5);
    private final ContextBuilder builder = new ContextBuilder(elementRepo, chapterRepo, ranker);

    @Test
    @DisplayName("设定总量超预算时，按相关性保留顶 K 条，且总长不超预算")
    void keepsRelevantElementsWhenOverBudget() {
        List<FrameworkElement> elements = new ArrayList<>();
        Framework fw = Framework.builder().id(1L).build();

        // 一条本章要用的设定
        elements.add(FrameworkElement.builder().id("target").version(1).framework(fw)
                .type("location").name("长安城")
                .content("唐朝都城，城门由禁军守卫，夜间宵禁").isDeprecated(false).build());

        // 大量无关设定，把总量顶过 2 万字预算（每条 ~720 字 × 40 条）
        String filler = "这段是与本章无关的设定描述，用来撑大上下文预算。".repeat(30);
        for (int i = 0; i < 40; i++) {
            elements.add(FrameworkElement.builder().id("filler-" + i).version(1).framework(fw)
                    .type("item").name("杂物" + i).content(filler).isDeprecated(false).build());
        }

        when(elementRepo.findLatestByFrameworkId(1L)).thenReturn(elements);
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(1L)).thenReturn(List.of());

        ContextBuilder.ContextPackage ctx = builder.buildContext(1L, "长安城 城门 禁军 宵禁");

        // 1) 本章要用的设定必须留下
        assertThat(ctx.systemContext()).contains("长安城");
        // 2) 总量受预算约束
        assertThat(ctx.systemContext().length()).isLessThanOrEqualTo(20000);
        // 3) 走的是"检索 top-K"而不是"全量拼接后截断"：否则会带进几十条无关设定
        long itemCount = ctx.systemContext().split("\\[item\\]", -1).length - 1;
        assertThat(itemCount).isLessThanOrEqualTo(5);
    }
}
