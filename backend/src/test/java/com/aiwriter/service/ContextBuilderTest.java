package com.aiwriter.service;

import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextBuilderTest {

    @Mock private FrameworkElementRepository elementRepo;
    @Mock private ChapterRepository chapterRepo;
    @InjectMocks private ContextBuilder contextBuilder;

    private List<FrameworkElement> elements;
    private List<Chapter> chapters;

    @BeforeEach
    void setUp() {
        Framework fw = Framework.builder().id(100L).build();

        elements = List.of(
                FrameworkElement.builder().id("e1").version(1).framework(fw)
                        .type("character").name("张三").content("主角，25岁剑客").isDeprecated(false).build(),
                FrameworkElement.builder().id("e2").version(1).framework(fw)
                        .type("location").name("长安").content("唐朝都城").isDeprecated(false).build()
        );

        chapters = List.of(
                Chapter.builder().id(1L).framework(fw).chapterIndex(1)
                        .document(Document.builder().id(10L).content("第一章内容...").build())
                        .summary("第一章摘要").status(Chapter.ChapterStatus.AI_GENERATED).build(),
                Chapter.builder().id(2L).framework(fw).chapterIndex(2)
                        .document(Document.builder().id(11L).content("第二章内容...").build())
                        .summary("第二章摘要").status(Chapter.ChapterStatus.AI_GENERATED).build()
        );
    }

    @Test
    void shouldBuildContextWithElementsAndRecentContent() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(elements);
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L)).thenReturn(chapters);

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(100L, "当前正在写的内容");

        assertNotNull(ctx);
        assertTrue(ctx.systemContext().contains("[character] 张三: 主角，25岁剑客"));
        assertTrue(ctx.systemContext().contains("[location] 长安: 唐朝都城"));
        assertTrue(ctx.recentContent().contains("第二章内容..."));
        assertTrue(ctx.recentContent().contains("当前正在写的内容"));
    }

    @Test
    void shouldIncludeEarlierSummaries() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(elements);
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L)).thenReturn(chapters);

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(100L, null);

        assertTrue(ctx.earlierSummaries().contains("第1章摘要: 第一章摘要"));
    }

    @Test
    void shouldHandleEmptyFramework() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of());
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L)).thenReturn(List.of());

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(100L, null);

        assertNotNull(ctx);
        assertTrue(ctx.systemContext().contains("【小说设定，写作时必须遵守】"));
        assertEquals("", ctx.recentContent().trim());
    }

    @Test
    void shouldTruncateSystemContextAtMaxChars() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            huge.append("[character] 角色").append(i)
                .append(": 这是一个很长的描述内容用来填充系统上下文")
                .append(i).append("\n\n");
        }
        FrameworkElement hugeEl = FrameworkElement.builder()
                .id("big").version(1).type("character").name("大").content(huge.toString())
                .isDeprecated(false).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(hugeEl));
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L)).thenReturn(List.of());

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(100L, null);

        assertTrue(ctx.systemContext().length() <= 20000);
    }

    @Test
    void shouldTruncateRecentContentAtMaxChars() {
        String longContent = "长".repeat(10000);
        Chapter longChapter = Chapter.builder().id(1L).chapterIndex(1)
                .document(Document.builder().id(10L).content(longContent).build())
                .status(Chapter.ChapterStatus.AI_GENERATED).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(elements);
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L)).thenReturn(List.of(longChapter));

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(100L, null);

        assertTrue(ctx.recentContent().length() <= 8000);
    }
}
