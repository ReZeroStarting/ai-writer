package com.aiwriter.controller;

import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.service.ElementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ElementController {
    private final ElementService elementService;

    @GetMapping("/frameworks/{frameworkId}/elements")
    public ResponseEntity<List<ElementResponse>> list(
            @PathVariable Long frameworkId,
            @RequestParam(required = false) String type,
            Authentication auth) {
        return ResponseEntity.ok(elementService.list(getUserId(auth), frameworkId, type));
    }

    @PostMapping("/frameworks/{frameworkId}/elements")
    public ResponseEntity<ElementResponse> create(
            @PathVariable Long frameworkId,
            @Valid @RequestBody FrameworkElementRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.create(getUserId(auth), frameworkId, req));
    }

    @PutMapping("/frameworks/{frameworkId}/elements/{elementId}")
    public ResponseEntity<ElementResponse> update(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            @Valid @RequestBody FrameworkElementRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.update(getUserId(auth), frameworkId, elementId, req));
    }

    @DeleteMapping("/frameworks/{frameworkId}/elements/{elementId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        elementService.delete(getUserId(auth), frameworkId, elementId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/frameworks/{frameworkId}/elements/{elementId}/versions")
    public ResponseEntity<List<ElementResponse>> versions(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        return ResponseEntity.ok(elementService.getVersions(getUserId(auth), frameworkId, elementId));
    }

    @PostMapping("/frameworks/{frameworkId}/elements/ai-generate")
    public ResponseEntity<List<ElementResponse>> aiGenerate(
            @PathVariable Long frameworkId,
            @Valid @RequestBody AiGenerateElementsRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.aiGenerate(getUserId(auth), frameworkId, req));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
