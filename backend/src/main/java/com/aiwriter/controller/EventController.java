package com.aiwriter.controller;

import com.aiwriter.dto.response.WritingEventResponse;
import com.aiwriter.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @GetMapping("/frameworks/{frameworkId}/events")
    public ResponseEntity<List<WritingEventResponse>> list(
            @PathVariable Long frameworkId, Authentication auth) {
        return ResponseEntity.ok(eventService.list(getUserId(auth), frameworkId));
    }

    @GetMapping("/frameworks/{frameworkId}/events/{timestamp}")
    public ResponseEntity<List<WritingEventResponse>> upTo(
            @PathVariable Long frameworkId,
            @PathVariable String timestamp, Authentication auth) {
        LocalDateTime until = LocalDateTime.parse(timestamp);
        return ResponseEntity.ok(eventService.listUpTo(getUserId(auth), frameworkId, until));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
