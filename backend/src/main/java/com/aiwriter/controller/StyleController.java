package com.aiwriter.controller;

import com.aiwriter.dto.request.LearnRequest;
import com.aiwriter.dto.request.StyleRequest;
import com.aiwriter.dto.response.JobResponse;
import com.aiwriter.dto.response.StyleResponse;
import com.aiwriter.service.LearningService;
import com.aiwriter.service.StyleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StyleController {
    private final StyleService styleService;
    private final LearningService learningService;

    @GetMapping("/styles")
    public ResponseEntity<List<StyleResponse>> list(Authentication auth) {
        return ResponseEntity.ok(styleService.list(getUserId(auth)));
    }

    @PostMapping("/styles")
    public ResponseEntity<StyleResponse> create(Authentication auth, @Valid @RequestBody StyleRequest req) {
        return ResponseEntity.ok(styleService.create(getUserId(auth), req));
    }

    @GetMapping("/styles/{id}")
    public ResponseEntity<StyleResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(styleService.get(getUserId(auth), id));
    }

    @PutMapping("/styles/{id}")
    public ResponseEntity<StyleResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody StyleRequest req) {
        return ResponseEntity.ok(styleService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/styles/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        styleService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/styles/{id}/learn")
    public ResponseEntity<JobResponse> learn(Authentication auth, @PathVariable Long id, @Valid @RequestBody LearnRequest req) {
        return ResponseEntity.ok(learningService.startLearning(getUserId(auth), id, req));
    }

    @GetMapping("/styles/{id}/jobs")
    public ResponseEntity<List<JobResponse>> jobs(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(learningService.listJobs(getUserId(auth), id));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> job(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(learningService.getJob(getUserId(auth), id));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
