package com.aiwriter.controller;

import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping(value = "/generate-outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateOutline(@Valid @RequestBody AiRequest req) {
        return aiService.generateOutline(req);
    }

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(@Valid @RequestBody AiRequest req) {
        return aiService.continueWrite(req);
    }

    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> polish(@Valid @RequestBody AiRequest req) {
        return aiService.polish(req);
    }

    @PostMapping(value = "/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> expand(@Valid @RequestBody AiRequest req) {
        return aiService.expand(req);
    }

    @PostMapping(value = "/shorten", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> shorten(@Valid @RequestBody AiRequest req) {
        return aiService.shorten(req);
    }

    @PostMapping(value = "/auto-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> autoWrite(@Valid @RequestBody AiRequest req) {
        return aiService.autoWrite(req);
    }

    @PostMapping(value = "/imitate-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> imitateWrite(@Valid @RequestBody AiRequest req) {
        return aiService.imitateWrite(req);
    }
}
