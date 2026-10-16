package com.aiwriter.controller;

import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping(value = "/generate-outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateOutline(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.generateOutline(req, getUserId(auth));
    }

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.continueWrite(req, getUserId(auth));
    }

    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> polish(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.polish(req, getUserId(auth));
    }

    @PostMapping(value = "/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> expand(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.expand(req, getUserId(auth));
    }

    @PostMapping(value = "/shorten", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> shorten(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.shorten(req, getUserId(auth));
    }

    @PostMapping(value = "/auto-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> autoWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.autoWrite(req, getUserId(auth));
    }

    @PostMapping(value = "/imitate-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> imitateWrite(Authentication auth, @Valid @RequestBody AiRequest req) {
        return aiService.imitateWrite(req, getUserId(auth));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
