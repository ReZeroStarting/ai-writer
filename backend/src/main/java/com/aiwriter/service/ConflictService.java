package com.aiwriter.service;

import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConflictService {
    private final ConflictRepository conflictRepo;
    private final PatchRepository patchRepo;
    private final ChapterRepository chapterRepo;
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final EventService eventService;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<ConflictResponse> listByFramework(Long userId, Long frameworkId) {
        frameworkService.findOwned(userId, frameworkId);
        return conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(frameworkId).stream()
                .map(this::toResponse).toList();
    }

    public ConflictResponse resolve(Long userId, Long frameworkId, Long conflictId, ResolveConflictRequest req) {
        frameworkService.findOwned(userId, frameworkId);
        Conflict c = conflictRepo.findById(conflictId)
                .orElseThrow(() -> new NotFoundException("冲突不存在"));
        // 交叉越权防护：先验框架归属后，还必须确认该冲突确实属于这个框架，
        // 否则「我的框架 + 他人的 conflictId」可篡改他人冲突状态。
        if (c.getFramework() == null || !frameworkId.equals(c.getFramework().getId())) {
            throw new ForbiddenException("该冲突不属于指定框架");
        }

        switch (req.getAction()) {
            case "ignore":
                c.setStatus(Conflict.ConflictStatus.IGNORED);
                break;
            case "patch":
                c.setStatus(Conflict.ConflictStatus.PATCHED);
                Patch patch = Patch.builder().conflict(c).content(req.getPatchContent())
                        .affectedChapterIds("[]").build();
                patchRepo.save(patch);
                eventService.appendEventInternal(c.getFramework(), WritingEvent.EventType.PATCH_APPLIED,
                        Map.of("conflictId", c.getId(), "patchContent", req.getPatchContent()));
                break;
            case "rewrite":
                c.setStatus(Conflict.ConflictStatus.REWRITTEN);
                break;
            default:
                throw new IllegalArgumentException("无效的操作: " + req.getAction());
        }
        conflictRepo.save(c);
        eventService.appendEventInternal(c.getFramework(), WritingEvent.EventType.CONFLICT_RESOLVED,
                Map.of("conflictId", c.getId(), "resolution", req.getAction()));
        return toResponse(c);
    }

    public List<Long> getImpactedChapters(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        // 元素必须属于该框架，否则会拿他人元素的版本号参与本框架计算（一致性 + 防御性）
        elementRepo.findLatestByElementId(elementId).ifPresent(el -> {
            if (el.getFramework() == null || !frameworkId.equals(el.getFramework().getId())) {
                throw new ForbiddenException("该元素不属于指定框架");
            }
        });
        var latestVersion = elementRepo.findMaxVersionByElementId(elementId).orElse(1);
        return chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId).stream()
                .filter(ch -> {
                    Map<String, Integer> snap = parseSnapshot(ch.getElementSnapshot());
                    Integer refVersion = snap.get(elementId);
                    return refVersion != null && refVersion < latestVersion;
                })
                .map(Chapter::getId)
                .toList();
    }

    /**
     * Check a newly generated chapter against current element versions.
     * Returns list of conflicts found. All conflicts start as PENDING.
     */
    public List<Conflict> checkChapterConsistency(Framework fw, Chapter chapter, String aiGeneratedContent) {
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, Integer> snapshot = parseSnapshot(chapter.getElementSnapshot());
        var currentElements = elementRepo.findLatestByFrameworkId(fw.getId());

        for (var el : currentElements) {
            Integer snapVer = snapshot.get(el.getId());
            if (snapVer != null && snapVer < el.getVersion()) {
                Conflict c = Conflict.builder()
                        .framework(fw).elementId(el.getId()).chapter(chapter)
                        .severity(Conflict.ConflictSeverity.MINOR)
                        .description("元素 [" + el.getName() + "] 在章节生成后被修改（v" + snapVer + " → v" + el.getVersion() + "）")
                        .chapterRefVersion(snapVer)
                        .currentVersion(el.getVersion())
                        .status(Conflict.ConflictStatus.PENDING)
                        .build();
                conflictRepo.save(c);
                conflicts.add(c);
                eventService.appendEventInternal(fw, WritingEvent.EventType.CONFLICT_DETECTED,
                        Map.of("conflictId", c.getId(), "severity", "MINOR", "elementId", el.getId()));
            }
        }
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseSnapshot(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            Map<String, Object> raw = mapper.readValue(json, Map.class);
            Map<String, Integer> result = new HashMap<>();
            for (var entry : raw.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer i) result.put(entry.getKey(), i);
                else if (value instanceof Number n) result.put(entry.getKey(), n.intValue());
            }
            return result;
        } catch (Exception e) { return Map.of(); }
    }

    private ConflictResponse toResponse(Conflict c) {
        return ConflictResponse.builder()
                .id(c.getId()).elementId(c.getElementId())
                .chapterId(c.getChapter().getId())
                .chapterRefVersion(c.getChapterRefVersion())
                .currentVersion(c.getCurrentVersion())
                .severity(c.getSeverity().name()).description(c.getDescription())
                .status(c.getStatus().name()).createdAt(c.getCreatedAt().toString())
                .build();
    }
}
