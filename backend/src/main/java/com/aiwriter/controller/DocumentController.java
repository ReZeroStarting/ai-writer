package com.aiwriter.controller;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService docService;

    @GetMapping("/projects/{projectId}/docs")
    public ResponseEntity<List<DocumentResponse>> list(Authentication auth, @PathVariable Long projectId) {
        return ResponseEntity.ok(docService.listByProject(getUserId(auth), projectId));
    }

    @PostMapping("/projects/{projectId}/docs")
    public ResponseEntity<DocumentResponse> create(Authentication auth, @PathVariable Long projectId, @Valid @RequestBody DocumentRequest req) {
        return ResponseEntity.ok(docService.create(getUserId(auth), projectId, req));
    }

    @GetMapping("/docs/{id}")
    public ResponseEntity<DocumentResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(docService.get(getUserId(auth), id));
    }

    @PutMapping("/docs/{id}")
    public ResponseEntity<DocumentResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody DocumentRequest req) {
        return ResponseEntity.ok(docService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/docs/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        docService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
