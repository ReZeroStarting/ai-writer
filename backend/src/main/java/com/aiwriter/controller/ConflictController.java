package com.aiwriter.controller;

import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.service.ConflictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConflictController {
    private final ConflictService conflictService;

    @GetMapping("/frameworks/{frameworkId}/conflicts")
    public ResponseEntity<List<ConflictResponse>> list(@PathVariable Long frameworkId, Authentication auth) {
        return ResponseEntity.ok(conflictService.listByFramework(getUserId(auth), frameworkId));
    }

    @PostMapping("/frameworks/{frameworkId}/conflicts/{conflictId}/resolve")
    public ResponseEntity<ConflictResponse> resolve(
            @PathVariable Long frameworkId,
            @PathVariable Long conflictId,
            @Valid @RequestBody ResolveConflictRequest req,
            Authentication auth) {
        return ResponseEntity.ok(conflictService.resolve(getUserId(auth), frameworkId, conflictId, req));
    }

    @PostMapping("/frameworks/{frameworkId}/elements/{elementId}/impact")
    public ResponseEntity<List<Long>> impact(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        return ResponseEntity.ok(conflictService.getImpactedChapters(getUserId(auth), frameworkId, elementId));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
