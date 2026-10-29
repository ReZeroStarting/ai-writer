package com.aiwriter.service;

import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConflictServiceTest {

    @Mock private ConflictRepository conflictRepo;
    @Mock private PatchRepository patchRepo;
    @Mock private ChapterRepository chapterRepo;
    @Mock private FrameworkElementRepository elementRepo;
    @Mock private FrameworkService frameworkService;
    @Mock private EventService eventService;
    @Mock private com.aiwriter.ai.AiProvider aiProvider;
    @InjectMocks private ConflictService conflictService;

    private Framework framework;
    private Chapter chapter;
    private Conflict conflict;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).build();
        framework = Framework.builder().id(100L).user(user)
                .status(Framework.FrameworkStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        chapter = Chapter.builder().id(1L).framework(framework).chapterIndex(1)
                .document(Document.builder().id(10L).build())
                .elementSnapshot("{\"e1\": 1}")
                .status(Chapter.ChapterStatus.AI_GENERATED)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        conflict = Conflict.builder().id(10L).framework(framework).elementId("e1")
                .chapter(chapter).severity(Conflict.ConflictSeverity.MINOR)
                .description("元素版本冲突")
                .chapterRefVersion(1).currentVersion(2)
                .status(Conflict.ConflictStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldListConflictsByFramework() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(conflict));

        List<ConflictResponse> results = conflictService.listByFramework(1L, 100L);

        assertEquals(1, results.size());
        assertEquals("PENDING", results.get(0).getStatus());
    }

    @Test
    void shouldResolveConflictByIgnore() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(conflictRepo.findById(10L)).thenReturn(Optional.of(conflict));
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        ResolveConflictRequest req = new ResolveConflictRequest();
        req.setAction("ignore");

        ConflictResponse response = conflictService.resolve(1L, 100L, 10L, req);

        assertEquals("IGNORED", response.getStatus());
        verify(conflictRepo).save(conflict);
        verify(eventService, atLeastOnce()).appendEventInternal(any(), any(), any());
    }

    @Test
    void shouldResolveConflictByPatch() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(conflictRepo.findById(10L)).thenReturn(Optional.of(conflict));
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        ResolveConflictRequest req = new ResolveConflictRequest();
        req.setAction("patch");
        req.setPatchContent("手动修复内容");

        ConflictResponse response = conflictService.resolve(1L, 100L, 10L, req);

        assertEquals("PATCHED", response.getStatus());
        verify(patchRepo).save(any(Patch.class));
    }

    @Test
    void shouldResolveConflictByRewrite() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(conflictRepo.findById(10L)).thenReturn(Optional.of(conflict));
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        ResolveConflictRequest req = new ResolveConflictRequest();
        req.setAction("rewrite");

        ConflictResponse response = conflictService.resolve(1L, 100L, 10L, req);

        assertEquals("REWRITTEN", response.getStatus());
    }

    @Test
    void shouldRejectInvalidAction() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(conflictRepo.findById(10L)).thenReturn(Optional.of(conflict));

        ResolveConflictRequest req = new ResolveConflictRequest();
        req.setAction("invalid");

        assertThrows(IllegalArgumentException.class,
                () -> conflictService.resolve(1L, 100L, 10L, req));
        verify(conflictRepo, never()).save(any());
    }

    @Test
    void shouldFindImpactedChapters() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findMaxVersionByElementId("e1")).thenReturn(Optional.of(2));
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L))
                .thenReturn(List.of(chapter));

        List<Long> impacted = conflictService.getImpactedChapters(1L, 100L, "e1");

        assertEquals(1, impacted.size());
        assertEquals(1L, impacted.get(0));
    }

    @Test
    void shouldReturnEmptyImpactWhenNoChaptersAffected() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findMaxVersionByElementId("e1")).thenReturn(Optional.of(1));
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(100L))
                .thenReturn(List.of(chapter));

        List<Long> impacted = conflictService.getImpactedChapters(1L, 100L, "e1");

        assertTrue(impacted.isEmpty());
    }

    @Test
    void shouldDetectChapterConsistencyConflicts() {
        FrameworkElement updated = FrameworkElement.builder()
                .id("e1").version(2).framework(framework)
                .name("张三").content("更新后").isDeprecated(false).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(updated));
        when(conflictRepo.save(any(Conflict.class))).thenAnswer(inv -> {
            Conflict c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10L);
            return c;
        });
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        List<Conflict> conflicts = conflictService.checkChapterConsistency(framework, chapter, "AI生成内容");

        assertEquals(1, conflicts.size());
        assertEquals(Conflict.ConflictSeverity.MINOR, conflicts.get(0).getSeverity());
        assertTrue(conflicts.get(0).getDescription().contains("张三"));
        verify(conflictRepo).save(any(Conflict.class));
    }

    @Test
    void shouldDetectNoConflictsWhenVersionsMatch() {
        FrameworkElement current = FrameworkElement.builder()
                .id("e1").version(1).framework(framework)
                .name("张三").content("未变").isDeprecated(false).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(current));

        List<Conflict> conflicts = conflictService.checkChapterConsistency(framework, chapter, "AI生成内容");

        assertTrue(conflicts.isEmpty());
        verify(conflictRepo, never()).save(any(Conflict.class));
    }

    // ---------------------------------------------------------------- AI 语义检查

    private static final String LONG_TEXT = "雨下了一整夜。".repeat(40);

    private FrameworkElement element(String id) {
        return FrameworkElement.builder()
                .id(id).version(1).framework(framework)
                .name("沈砚").type("character").content("盲人剑客，左手使剑，目光锐利").isDeprecated(false).build();
    }

    @Test
    void aiCheckShouldRecordConflictsFromModelJson() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element("e1")));
        when(aiProvider.complete(any(), any())).thenReturn(
                "```json\n[{\"elementId\":\"e1\",\"severity\":\"MAJOR\","
                        + "\"description\":\"设定写沈砚是盲人，正文却写他「目光锐利地扫过全场」\"}]\n```");
        when(conflictRepo.save(any(Conflict.class))).thenAnswer(inv -> {
            Conflict c = inv.getArgument(0);
            if (c.getId() == null) c.setId(99L);
            return c;
        });
        when(eventService.appendEventInternal(any(), any(), any())).thenReturn(null);

        int created = conflictService.aiCheckAgainstElements(framework, chapter, LONG_TEXT);

        assertEquals(1, created);
        verify(conflictRepo).save(any(Conflict.class));
        verify(eventService).appendEventInternal(any(), any(), any());
    }

    @Test
    void aiCheckShouldDropUnknownElementIds() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element("e1")));
        when(aiProvider.complete(any(), any())).thenReturn(
                "[{\"elementId\":\"not-a-real-element\",\"severity\":\"MAJOR\",\"description\":\"胡说\"}]");

        int created = conflictService.aiCheckAgainstElements(framework, chapter, LONG_TEXT);

        assertEquals(0, created);
        verify(conflictRepo, never()).save(any(Conflict.class));
    }

    @Test
    void aiCheckShouldSwallowUnparseableOutput() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element("e1")));
        when(aiProvider.complete(any(), any())).thenReturn("我觉得这一章写得不错，没有矛盾。");

        int created = conflictService.aiCheckAgainstElements(framework, chapter, LONG_TEXT);

        assertEquals(0, created);
        verify(conflictRepo, never()).save(any(Conflict.class));
    }

    @Test
    void aiCheckShouldSkipShortChaptersAndEmptyFrameworks() {
        assertEquals(0, conflictService.aiCheckAgainstElements(framework, chapter, "太短了"));
        verify(aiProvider, never()).complete(any(), any());

        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of());
        assertEquals(0, conflictService.aiCheckAgainstElements(framework, chapter, LONG_TEXT));
        verify(aiProvider, never()).complete(any(), any());
    }

    @Test
    void aiCheckShouldNotPropagateUpstreamFailure() {
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element("e1")));
        when(aiProvider.complete(any(), any())).thenThrow(new IllegalStateException("502 上游错误"));

        assertEquals(0, conflictService.aiCheckAgainstElements(framework, chapter, LONG_TEXT));
        verify(conflictRepo, never()).save(any(Conflict.class));
    }
}
