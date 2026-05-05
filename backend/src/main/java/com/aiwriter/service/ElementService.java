package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ElementService {
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final ClaudeService claudeService;

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
        return toResponse(elementRepo.save(el));
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
        return toResponse(elementRepo.save(el));
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
        Framework fw = frameworkService.findOwned(userId, frameworkId);

        // Build context from existing elements to avoid conflicts
        List<FrameworkElement> existing = elementRepo.findLatestByFrameworkId(frameworkId);
        StringBuilder existingCtx = new StringBuilder("【已有设定，生成时不能与之冲突】\n");
        for (var e : existing) {
            existingCtx.append("- [").append(e.getType()).append("] ")
                       .append(e.getName()).append(": ").append(e.getContent()).append("\n");
        }

        String systemPrompt = "你是一位小说设定顾问。根据用户需求生成新的设定元素，必须与已有设定不冲突。返回 JSON 数组，每项包含 name 和 content 字段。";
        String userPrompt = existingCtx + "\n请生成类型为 [" + req.getType() + "] 的元素";
        if (req.getHint() != null && !req.getHint().isBlank()) {
            userPrompt += "，参考提示：" + req.getHint();
        }
        userPrompt += "。仅返回 JSON 数组，不要其他文字。";

        String response = claudeService.complete(systemPrompt, userPrompt);
        return parseGeneratedElements(fw, userId, req.getType(), response);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<ElementResponse> parseGeneratedElements(Framework fw, Long userId, String type, String response) {
        try {
            var list = objectMapper.readValue(response, List.class);
            List<ElementResponse> results = new ArrayList<>();
            for (var item : list) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) item;
                String uuid = UUID.randomUUID().toString();
                FrameworkElement el = FrameworkElement.builder()
                        .id(uuid).version(1).framework(fw)
                        .user(User.builder().id(userId).build())
                        .type(type).name(map.get("name")).content(map.get("content"))
                        .isDeprecated(false).build();
                results.add(toResponse(elementRepo.save(el)));
            }
            return results;
        } catch (Exception e) {
            String uuid = UUID.randomUUID().toString();
            FrameworkElement el = FrameworkElement.builder()
                    .id(uuid).version(1).framework(fw)
                    .user(User.builder().id(userId).build())
                    .type(type).name("AI 生成").content(response)
                    .isDeprecated(false).build();
            return List.of(toResponse(elementRepo.save(el)));
        }
    }

    private ElementResponse toResponse(FrameworkElement e) {
        return ElementResponse.builder()
                .id(e.getId()).version(e.getVersion())
                .type(e.getType()).name(e.getName()).content(e.getContent())
                .isDeprecated(e.getIsDeprecated())
                .createdAt(e.getCreatedAt().toString()).build();
    }
}
