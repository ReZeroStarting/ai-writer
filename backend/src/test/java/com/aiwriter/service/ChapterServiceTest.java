package com.aiwriter.service;

import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChapterServiceTest {

    @Mock private ChapterRepository chapterRepo;
    @Mock private FrameworkElementRepository elementRepo;
    @Mock private EventService eventService;
    @InjectMocks private ChapterService chapterService;

    private Framework framework;
    private Chapter chapter;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).build();
        framework = Framework.builder().id(100L).user(user)
                .status(Framework.FrameworkStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        chapter = Chapter.builder().id(1L).framework(framework).chapterIndex(1)
                .document(Document.builder().id(10L).build())
                .elementSnapshot("{\"e1\":1,\"e2\":2}")
                .status(Chapter.ChapterStatus.DRAFT)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldCreateChapterWithElementSnapshot() {
        FrameworkElement e1 = FrameworkElement.builder().id("e1").version(1).framework(framework)
                .type("character").name("张三").content("主角").isDeprecated(false).build();
        FrameworkElement e2 = FrameworkElement.builder().id("e2").version(2).framework(framework)
                .type("location").name("长安").content("都城").isDeprecated(false).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(e1, e2));
        when(chapterRepo.save(any(Chapter.class))).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        Chapter result = chapterService.createChapter(framework, 1, 10L);

        assertNotNull(result);
        assertEquals(Chapter.ChapterStatus.DRAFT, result.getStatus());
        assertEquals(1, result.getChapterIndex());
        assertTrue(result.getElementSnapshot().contains("e1"));
        assertTrue(result.getElementSnapshot().contains("e2"));
        verify(chapterRepo).save(any(Chapter.class));
    }

    @Test
    void shouldMarkGenerating() {
        when(chapterRepo.findById(1L)).thenReturn(Optional.of(chapter));
        when(chapterRepo.save(any(Chapter.class))).thenReturn(chapter);

        Chapter result = chapterService.markGenerating(1L);

        assertEquals(Chapter.ChapterStatus.AI_GENERATING, result.getStatus());
    }

    @Test
    void shouldThrowNotFoundOnMarkGenerating() {
        when(chapterRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> chapterService.markGenerating(999L));
    }

    @Test
    void shouldMarkGenerated() {
        when(chapterRepo.findById(1L)).thenReturn(Optional.of(chapter));
        when(chapterRepo.save(any(Chapter.class))).thenReturn(chapter);
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        Chapter result = chapterService.markGenerated(1L, "章节摘要内容");

        assertEquals(Chapter.ChapterStatus.AI_GENERATED, result.getStatus());
        assertEquals("章节摘要内容", result.getSummary());
    }

    @Test
    void shouldGetLatestChapter() {
        when(chapterRepo.findTopByFrameworkIdOrderByChapterIndexDesc(100L))
                .thenReturn(Optional.of(chapter));

        Chapter result = chapterService.getLatestChapter(100L);

        assertNotNull(result);
        assertEquals(1, result.getChapterIndex());
    }

    @Test
    void shouldReturnNullWhenNoChapters() {
        when(chapterRepo.findTopByFrameworkIdOrderByChapterIndexDesc(100L))
                .thenReturn(Optional.empty());

        Chapter result = chapterService.getLatestChapter(100L);

        assertNull(result);
    }

    @Test
    void shouldListChapters() {
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L))
                .thenReturn(List.of(chapter));

        List<Chapter> results = chapterService.list(100L);

        assertEquals(1, results.size());
    }

    @Test
    void shouldGetElementSnapshot() {
        when(chapterRepo.findById(1L)).thenReturn(Optional.of(chapter));

        Map<String, Integer> snapshot = chapterService.getElementSnapshot(1L);

        assertEquals(2, snapshot.size());
        assertEquals(1, snapshot.get("e1"));
        assertEquals(2, snapshot.get("e2"));
    }

    @Test
    void shouldParseEmptyOrNullSnapshot() {
        when(chapterRepo.findById(1L)).thenReturn(Optional.of(Chapter.builder()
                .id(1L).elementSnapshot(null).build()));

        Map<String, Integer> snapshot = chapterService.getElementSnapshot(1L);

        assertTrue(snapshot.isEmpty());
    }

    @Test
    void shouldConvertToResponse() {
        ChapterResponse response = chapterService.toResponse(chapter);

        assertEquals(1L, response.getId());
        assertEquals(1, response.getChapterIndex());
        assertEquals("DRAFT", response.getStatus());
        assertEquals(10L, response.getDocumentId());
    }

    @Test
    void shouldHandleChapterWithNullDocumentInResponse() {
        Chapter ch = Chapter.builder().id(2L).chapterIndex(2)
                .status(Chapter.ChapterStatus.DRAFT)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        ChapterResponse response = chapterService.toResponse(ch);

        assertNull(response.getDocumentId());
    }
}
