package com.aiwriter.controller;

import com.aiwriter.ai.SseText;
import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI 写作接口，全部以 SSE 流式返回。
 *
 * <p>返回值统一过 {@link SseText#escapeAll}（行首空格保护），且**不要**自己再拼
 * "data:" 前缀：Spring MVC 已经按 SSE 协议逐行加前缀并补空行。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping(value = "/generate-outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateOutline(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.generateOutline(req, getUserId(auth)));
    }

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.continueWrite(req, getUserId(auth)));
    }

    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> polish(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.polish(req, getUserId(auth)));
    }

    @PostMapping(value = "/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> expand(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.expand(req, getUserId(auth)));
    }

    @PostMapping(value = "/shorten", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> shorten(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.shorten(req, getUserId(auth)));
    }

    @PostMapping(value = "/auto-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> autoWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.autoWrite(req, getUserId(auth)));
    }

    @PostMapping(value = "/imitate-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> imitateWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return SseText.escapeAll(aiService.imitateWrite(req, getUserId(auth)));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
