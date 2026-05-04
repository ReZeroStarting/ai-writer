package com.aiwriter.controller;

import com.aiwriter.dto.request.InterveneRequest;
import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.model.Chapter;
import com.aiwriter.service.ChapterService;
import com.aiwriter.service.NovelWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WriteController {
    private final NovelWriteService novelWriteService;
    private final ChapterService chapterService;

    @PostMapping(value = "/frameworks/{frameworkId}/write/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> start(@PathVariable Long frameworkId, Authentication auth) {
        return novelWriteService.startWriting(getUserId(auth), frameworkId)
                .map(chunk -> "data: " + chunk + "\n\n");
    }

    @PostMapping(value = "/frameworks/{frameworkId}/write/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(@PathVariable Long frameworkId, Authentication auth) {
        return novelWriteService.continueWriting(getUserId(auth), frameworkId)
                .map(chunk -> "data: " + chunk + "\n\n");
    }

    @PostMapping("/frameworks/{frameworkId}/write/interrupt")
    public ResponseEntity<ChapterResponse> intervene(
            @PathVariable Long frameworkId,
            @RequestBody InterveneRequest req,
            Authentication auth) {
        Chapter latest = chapterService.getLatestChapter(frameworkId);
        if (latest == null) return ResponseEntity.notFound().build();
        novelWriteService.intervene(getUserId(auth), frameworkId, latest.getId(), req.getContent());
        ChapterResponse resp = chapterService.toResponse(
                chapterService.getLatestChapter(frameworkId));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/frameworks/{frameworkId}/write/save")
    public ResponseEntity<Void> saveChapter(
            @PathVariable Long frameworkId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long chapterId = ((Number) body.get("chapterId")).longValue();
        String content = (String) body.get("content");
        novelWriteService.saveChapterContent(chapterId, content);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/frameworks/{frameworkId}/write/latest-chapter")
    public ResponseEntity<ChapterResponse> latestChapter(
            @PathVariable Long frameworkId, Authentication auth) {
        Chapter ch = chapterService.getLatestChapter(frameworkId);
        return ch != null ? ResponseEntity.ok(chapterService.toResponse(ch)) : ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
