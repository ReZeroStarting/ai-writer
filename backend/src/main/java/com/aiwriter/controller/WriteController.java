package com.aiwriter.controller;

import com.aiwriter.ai.SseText;
import com.aiwriter.dto.request.InterveneRequest;
import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.model.Chapter;
import com.aiwriter.service.ChapterService;
import com.aiwriter.service.FrameworkService;
import com.aiwriter.service.NovelWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WriteController {
    private final NovelWriteService novelWriteService;
    private final ChapterService chapterService;
    private final FrameworkService frameworkService;

    @PostMapping(value = "/frameworks/{frameworkId}/write/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> start(@PathVariable Long frameworkId, Authentication auth) {
        // 不要自己拼 "data: ...\n\n"：Spring MVC 已按 SSE 协议加前缀、按行拆分并补空行，
        // 手拼会得到 "data:data: ..." 的双前缀（真实踩过的坑）。
        return SseText.escapeAll(novelWriteService.startWriting(getUserId(auth), frameworkId));
    }

    @PostMapping(value = "/frameworks/{frameworkId}/write/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(@PathVariable Long frameworkId, Authentication auth) {
        return SseText.escapeAll(novelWriteService.continueWriting(getUserId(auth), frameworkId));
    }

    @PostMapping("/frameworks/{frameworkId}/write/interrupt")
    public ResponseEntity<ChapterResponse> intervene(
            @PathVariable Long frameworkId,
            @RequestBody InterveneRequest req,
            Authentication auth) {
        Long userId = getUserId(auth);
        // 归属校验：不属于当前用户的框架直接 403，避免越权读取/注入
        frameworkService.findOwned(userId, frameworkId);
        Chapter latest = chapterService.getLatestChapter(frameworkId);
        if (latest == null) return ResponseEntity.notFound().build();
        novelWriteService.intervene(userId, frameworkId, latest.getId(), req.getContent());
        ChapterResponse resp = chapterService.toResponse(
                chapterService.getLatestChapter(frameworkId));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/frameworks/{frameworkId}/write/save")
    public ResponseEntity<Void> saveChapter(
            @PathVariable Long frameworkId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = getUserId(auth);
        // 归属校验：框架必须属于当前用户
        frameworkService.findOwned(userId, frameworkId);
        Object cid = body.get("chapterId");
        if (!(cid instanceof Number)) {
            throw new IllegalArgumentException("chapterId 缺失或格式错误");
        }
        Long chapterId = ((Number) cid).longValue();
        String content = (String) body.get("content");
        novelWriteService.saveChapterContent(userId, frameworkId, chapterId, content);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/frameworks/{frameworkId}/write/latest-chapter")
    public ResponseEntity<ChapterResponse> latestChapter(
            @PathVariable Long frameworkId, Authentication auth) {
        Long userId = getUserId(auth);
        // 归属校验：避免越权读取他人章节内容
        frameworkService.findOwned(userId, frameworkId);
        Chapter ch = chapterService.getLatestChapter(frameworkId);
        return ch != null ? ResponseEntity.ok(chapterService.toResponse(ch)) : ResponseEntity.noContent().build();
    }

    /**
     * 章节列表。
     *
     * <p>写作台的「章节列表」面板此前没有任何数据来源（前端从未拉取），所以永远显示「暂无章节」。
     */
    @GetMapping("/frameworks/{frameworkId}/chapters")
    public ResponseEntity<List<ChapterResponse>> chapters(
            @PathVariable Long frameworkId, Authentication auth) {
        frameworkService.findOwned(getUserId(auth), frameworkId);
        List<ChapterResponse> list = chapterService.list(frameworkId).stream()
                .map(chapterService::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
