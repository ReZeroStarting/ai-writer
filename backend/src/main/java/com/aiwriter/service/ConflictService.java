package com.aiwriter.service;

import com.aiwriter.ai.AiProvider;
import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConflictService {

    private static final Logger log = LoggerFactory.getLogger(ConflictService.class);

    /** 审校提示词：宁可漏报也不要瞎报，输出必须是纯 JSON 数组。 */
    private static final String CONFLICT_SYSTEM_PROMPT = """
            你是小说设定审校。给你【设定】与【本章正文】，找出正文中与设定**明显矛盾**的地方。

            只报告有明确证据的矛盾，例如：
            - 人物身份/性别/年龄/能力与设定不符（设定是盲人，正文却「眼神锐利」）
            - 地点归属、地理位置与设定不符
            - 物品归属或特性与设定不符
            - 设定中已死亡/未出场的角色在本章正常活动
            - 时间线冲突

            不要报告：文风、节奏、用词、逻辑偏好、设定里没有提到的内容。
            每条冲突必须指向【设定】里给出的某个 id。

            只输出 JSON 数组，最多 3 条，不要输出任何解释或代码块标记：
            [{"elementId":"<设定里的 id>","severity":"MAJOR|MINOR","description":"一句话说明矛盾"}]
            没有矛盾就输出 []。

            """;

    private final ConflictRepository conflictRepo;
    private final PatchRepository patchRepo;
    private final ChapterRepository chapterRepo;
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final EventService eventService;
    private final AiProvider aiProvider;
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

    /**
     * AI 语义一致性检查：把「最新设定元素」和「本章正文」一起交给模型，找出明显矛盾。
     *
     * <p>和 {@link #checkChapterConsistency} 的分工：
     * <ul>
     *   <li>版本检查是确定性的——元素在章节生成后被改过，就记一条冲突；</li>
     *   <li>这里是语义检查——正文写了「沈砚是瞎子」，设定写「左手使剑、目光锐利」才发现得了。</li>
     * </ul>
     *
     * <p>刻意保守：只在有设定元素且正文够长时才调用，最多记 3 条，模型返回任何
     * 解析不了的东西一律丢弃。检测失败绝不影响章节落库。
     *
     * @return 新建的冲突条数
     */
    public int aiCheckAgainstElements(Framework fw, Chapter chapter, String content) {
        if (content == null || content.length() < 200) return 0;
        var elements = elementRepo.findLatestByFrameworkId(fw.getId());
        if (elements.isEmpty()) return 0;

        Map<String, FrameworkElement> byId = new LinkedHashMap<>();
        StringBuilder spec = new StringBuilder();
        for (var el : elements) {
            if (spec.length() > 4000) break;
            byId.put(el.getId(), el);
            spec.append("- [").append(el.getId()).append("] ")
                    .append(el.getType()).append(' ').append(el.getName()).append("：")
                    .append(abbreviate(el.getContent(), 200)).append('\n');
        }

        String excerpt = content.length() > 6000 ? content.substring(0, 6000) : content;
        String raw;
        try {
            raw = aiProvider.complete(CONFLICT_SYSTEM_PROMPT,
                    "【设定】\n" + spec + "\n【本章正文】\n" + excerpt);
        } catch (Exception e) {
            log.warn("AI 冲突检测调用失败，跳过本章: {}", e.getMessage());
            return 0;
        }

        int created = 0;
        for (Finding f : parseFindings(raw)) {
            FrameworkElement el = byId.get(f.elementId);
            if (el == null) continue;  // 模型编了一个不存在的 id，丢弃
            Conflict c = Conflict.builder()
                    .framework(fw).elementId(el.getId()).chapter(chapter)
                    .severity("MAJOR".equalsIgnoreCase(f.severity)
                            ? Conflict.ConflictSeverity.MAJOR : Conflict.ConflictSeverity.MINOR)
                    .description(f.description)
                    .chapterRefVersion(el.getVersion())
                    .currentVersion(el.getVersion())
                    .status(Conflict.ConflictStatus.PENDING)
                    .build();
            conflictRepo.save(c);
            eventService.appendEventInternal(fw, WritingEvent.EventType.CONFLICT_DETECTED,
                    Map.of("conflictId", c.getId(), "severity", c.getSeverity().name(),
                            "elementId", el.getId(), "source", "ai"));
            created++;
        }
        return created;
    }

    private record Finding(String elementId, String severity, String description) {
    }

    /** 模型的输出可能是 ```json 包裹、可能带前后解释，只取第一个 JSON 数组。 */
    private List<Finding> parseFindings(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) return List.of();
        try {
            JsonNode arr = mapper.readTree(raw.substring(start, end + 1));
            if (!arr.isArray()) return List.of();
            List<Finding> out = new ArrayList<>();
            for (JsonNode node : arr) {
                if (out.size() >= 3) break;
                String id = node.path("elementId").asText("").trim();
                String desc = node.path("description").asText("").trim();
                if (id.isEmpty() || desc.isEmpty()) continue;
                out.add(new Finding(id, node.path("severity").asText("MINOR"), abbreviate(desc, 300)));
            }
            return out;
        } catch (Exception e) {
            log.warn("AI 冲突检测返回无法解析，忽略: {}", abbreviate(raw, 200));
            return List.of();
        }
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
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
