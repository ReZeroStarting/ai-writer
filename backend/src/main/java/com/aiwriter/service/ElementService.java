package com.aiwriter.service;

import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ElementService {
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;

    public List<ElementResponse> list(Long userId, Long frameworkId, String type) {
        frameworkService.findOwned(userId, frameworkId);
        return elementRepo.findLatestByFrameworkId(frameworkId).stream()
                .filter(e -> type == null || e.getType().equals(type))
                .map(this::toResponse).toList();
    }

    public ElementResponse create(Long userId, Long frameworkId, FrameworkElementRequest req) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        String uuid = UUID.randomUUID().toString();
        FrameworkElement el = FrameworkElement.builder()
                .id(uuid).version(1).framework(fw)
                .user(User.builder().id(userId).build())
                .type(req.getType()).name(req.getName()).content(req.getContent())
                .isDeprecated(false).build();
        elementRepo.save(el);
        return toResponse(el);
    }

    public ElementResponse update(Long userId, Long frameworkId, String elementId, FrameworkElementRequest req) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = elementRepo.findLatestByElementId(elementId)
                .orElseThrow(() -> new NotFoundException("元素不存在"));
        int newVersion = latest.getVersion() + 1;

        FrameworkElement el = FrameworkElement.builder()
                .id(elementId).version(newVersion).framework(latest.getFramework())
                .user(User.builder().id(userId).build())
                .type(req.getType()).name(req.getName()).content(req.getContent())
                .isDeprecated(false).build();
        elementRepo.save(el);
        return toResponse(el);
    }

    public void delete(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = elementRepo.findLatestByElementId(elementId)
                .orElseThrow(() -> new NotFoundException("元素不存在"));
        latest.setIsDeprecated(true);
        elementRepo.save(latest);
    }

    public List<ElementResponse> getVersions(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        return elementRepo.findVersionsByElementId(elementId).stream()
                .map(this::toResponse).toList();
    }

    public List<ElementResponse> aiGenerate(Long userId, Long frameworkId, AiGenerateElementsRequest req) {
        // Stub: returns empty list until ContextBuilder/ClaudeService integration in Phase 3
        return List.of();
    }

    private ElementResponse toResponse(FrameworkElement e) {
        return ElementResponse.builder()
                .id(e.getId()).version(e.getVersion())
                .type(e.getType()).name(e.getName()).content(e.getContent())
                .isDeprecated(e.getIsDeprecated())
                .createdAt(e.getCreatedAt().toString()).build();
    }
}
