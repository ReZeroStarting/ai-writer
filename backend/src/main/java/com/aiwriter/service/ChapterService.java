package com.aiwriter.service;

import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChapterService {
    private final ChapterRepository chapterRepo;
    private final FrameworkElementRepository elementRepo;
    private final EventService eventService;
    private final ObjectMapper mapper = new ObjectMapper();

    public Chapter createChapter(Framework fw, int chapterIndex, Long documentId) {
        List<FrameworkElement> elements = elementRepo.findLatestByFrameworkId(fw.getId());
        Map<String, Integer> snapshot = new HashMap<>();
        for (var e : elements) {
            snapshot.put(e.getId(), e.getVersion());
        }

        Chapter ch = Chapter.builder()
                .framework(fw)
                .document(Document.builder().id(documentId).build())
                .chapterIndex(chapterIndex)
                .elementSnapshot(toJson(snapshot))
                .status(Chapter.ChapterStatus.DRAFT)
                .build();
        chapterRepo.save(ch);

        eventService.appendEventInternal(fw, WritingEvent.EventType.CHAPTER_STARTED,
                Map.of("chapterId", ch.getId(), "chapterIndex", chapterIndex, "elementSnapshot", snapshot));
        return ch;
    }

    public Chapter markGenerating(Long chapterId) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        ch.setStatus(Chapter.ChapterStatus.AI_GENERATING);
        return chapterRepo.save(ch);
    }

    public Chapter markGenerated(Long chapterId, String summary) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        ch.setStatus(Chapter.ChapterStatus.AI_GENERATED);
        ch.setSummary(summary);
        chapterRepo.save(ch);

        eventService.appendEventInternal(ch.getFramework(), WritingEvent.EventType.CHAPTER_AI_GENERATED,
                Map.of("chapterId", ch.getId()));
        if (summary != null && !summary.isEmpty()) {
            eventService.appendEventInternal(ch.getFramework(), WritingEvent.EventType.CHAPTER_SUMMARY_GENERATED,
                    Map.of("chapterId", ch.getId(), "summary", summary));
        }
        return ch;
    }

    public Chapter getLatestChapter(Long frameworkId) {
        return chapterRepo.findTopByFrameworkIdOrderByChapterIndexDesc(frameworkId).orElse(null);
    }

    public List<Chapter> list(Long frameworkId) {
        return chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId);
    }

    public Map<String, Integer> getElementSnapshot(Long chapterId) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        if (ch.getElementSnapshot() == null) return Map.of();
        return parseSnapshot(ch.getElementSnapshot());
    }

    @SuppressWarnings("unchecked")
    Map<String, Integer> parseSnapshot(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            Map<String, Object> raw = mapper.readValue(json, Map.class);
            Map<String, Integer> result = new HashMap<>();
            for (var entry : raw.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer i) result.put(entry.getKey(), i);
                else if (value instanceof Number n) result.put(entry.getKey(), n.intValue());
            }
            return result;
        } catch (Exception e) { return Map.of(); }
    }

    public ChapterResponse toResponse(Chapter ch) {
        return ChapterResponse.builder()
                .id(ch.getId())
                .documentId(ch.getDocument() != null ? ch.getDocument().getId() : null)
                .chapterIndex(ch.getChapterIndex())
                .status(ch.getStatus().name())
                .summary(ch.getSummary())
                .createdAt(ch.getCreatedAt().toString())
                .updatedAt(ch.getUpdatedAt().toString())
                .build();
    }

    private String toJson(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
