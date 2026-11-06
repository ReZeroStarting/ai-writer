package com.aiwriter.service;

import com.aiwriter.ai.AiProvider;
import com.aiwriter.ai.AiProviderResolver;
import com.aiwriter.model.Chapter;
import com.aiwriter.model.Document;
import com.aiwriter.model.Framework;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ChapterRepository;
import com.aiwriter.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 续写入口的关键行为：**上一章正文要作为 RAG 检索查询**。
 *
 * <p>为什么专门测这一条：查询词是空串时 {@code ElementRanker} 会直接返回原顺序，
 * 设定一条都不会按相关性筛——接了向量库也等于白接，而且日志里只表现为
 * "保留 20 / 20"，很容易被忽略。这个测试就是防止它被改回去。
 */
@ExtendWith(MockitoExtension.class)
class NovelWriteServiceContinueTest {

    @Mock private AiProviderResolver aiProvider;
    @Mock private ContextBuilder contextBuilder;
    @Mock private ChapterService chapterService;
    @Mock private ConflictService conflictService;
    @Mock private FrameworkService frameworkService;
    @Mock private DocumentRepository documentRepo;
    @Mock private ChapterRepository chapterRepo;
    @Mock private AiProvider provider;

    private NovelWriteService service;
    private Framework framework;

    @BeforeEach
    void setUp() {
        service = new NovelWriteService(aiProvider, contextBuilder, chapterService,
                conflictService, frameworkService, documentRepo, chapterRepo);

        framework = Framework.builder()
                .id(100L)
                .user(User.builder().id(7L).build())
                .project(Project.builder().id(9L).build())
                .status(Framework.FrameworkStatus.ACTIVE)
                .build();

        lenient().when(frameworkService.findOwned(7L, 100L)).thenReturn(framework);
        lenient().when(aiProvider.forUser(7L)).thenReturn(provider);
        lenient().when(provider.stream(anyString(), anyString())).thenReturn(Flux.just("新写的一段"));
        lenient().when(contextBuilder.buildContext(anyLong(), anyString()))
                .thenReturn(new ContextBuilder.ContextPackage("设定块", "前文块", "摘要块"));
        lenient().when(documentRepo.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(55L);
            return d;
        });
        lenient().when(chapterService.createChapter(any(), anyInt(), anyLong()))
                .thenAnswer(inv -> Chapter.builder().id(56L).chapterIndex(2)
                        .framework(framework).status(Chapter.ChapterStatus.DRAFT).build());
        lenient().when(chapterService.markGenerating(anyLong()))
                .thenAnswer(inv -> Chapter.builder().id(56L).chapterIndex(2)
                        .framework(framework).status(Chapter.ChapterStatus.AI_GENERATED).build());
    }

    @Test
    @DisplayName("有上一章时：用上一章正文当 RAG 查询，而不是空串")
    void usesPreviousChapterContentAsQuery() {
        Document prevDoc = Document.builder().id(1L).content("<p>沈砚把青锋横在膝上</p>").build();
        Chapter prev = Chapter.builder().id(1L).chapterIndex(1).document(prevDoc)
                .framework(framework).status(Chapter.ChapterStatus.AI_GENERATED).build();
        when(chapterService.getLatestChapter(100L)).thenReturn(prev);

        service.continueWriting(7L, 100L).collectList().block();

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(contextBuilder).buildContext(eq(100L), query.capture());
        assertThat(query.getValue())
                .as("RAG 查询应当是上一章纯文本")
                .contains("沈砚把青锋横在膝上")
                .doesNotContain("<p>");
    }

    @Test
    @DisplayName("没有上一章时：查询为空，允许走默认顺序")
    void emptyQueryWhenNoPreviousChapter() {
        when(chapterService.getLatestChapter(100L)).thenReturn(null);

        service.continueWriting(7L, 100L).collectList().block();

        verify(contextBuilder).buildContext(100L, "");
    }

    @Test
    @DisplayName("续写仍然会真正调用上游并把内容写回章节")
    void stillGeneratesAndPersists() {
        when(chapterService.getLatestChapter(100L)).thenReturn(null);

        List<String> out = service.continueWriting(7L, 100L).collectList().block();

        assertThat(out).containsExactly("新写的一段");
        verify(provider).stream(anyString(), anyString());
        verify(chapterService).completeGeneration(eq(56L), anyString());
    }
}
