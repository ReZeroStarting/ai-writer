package com.aiwriter.controller;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(Authentication auth) {
        return ResponseEntity.ok(projectService.list(getUserId(auth)));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(Authentication auth, @Valid @RequestBody ProjectRequest req) {
        return ResponseEntity.ok(projectService.create(getUserId(auth), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return ResponseEntity.ok(projectService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        projectService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
