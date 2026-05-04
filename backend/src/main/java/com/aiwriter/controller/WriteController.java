package com.aiwriter.controller;

import com.aiwriter.dto.request.InterveneRequest;
import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.service.ChapterService;
import com.aiwriter.service.NovelWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
        // Stub: saves user input and triggers continue in Phase 3
        return ResponseEntity.ok().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
