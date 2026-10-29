package com.aiwriter.controller;

import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.service.FrameworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FrameworkController {
    private final FrameworkService frameworkService;

    @PostMapping("/projects/{projectId}/framework")
    public ResponseEntity<FrameworkResponse> open(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(frameworkService.openFramework(getUserId(auth), projectId));
    }

    @GetMapping("/projects/{projectId}/framework")
    public ResponseEntity<FrameworkResponse> getByProject(@PathVariable Long projectId, Authentication auth) {
        // 未开启框架 → 204，而不是 404：前端用它判断「要不要创建」，不该在控制台报错
        FrameworkResponse fw = frameworkService.findByProjectOrNull(getUserId(auth), projectId);
        return fw == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(fw);
    }

    @GetMapping("/frameworks/{id}")
    public ResponseEntity<FrameworkResponse> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(frameworkService.getFramework(getUserId(auth), id));
    }

    @PostMapping("/frameworks/{id}/finish")
    public ResponseEntity<FrameworkResponse> finish(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(frameworkService.finishFramework(getUserId(auth), id));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
