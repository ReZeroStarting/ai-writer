package com.aiwriter.service;

import com.aiwriter.ai.AiProviderResolver;
import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
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
public class ElementService {
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final AiProviderResolver aiProvider;
    /** 向量索引（可选）：配 ai.rag.vector-store=qdrant 才有实现，否则为 null。 */
    private final org.springframework.beans.factory.ObjectProvider<com.aiwriter.ai.rag.VectorIndex> vectorIndexes;

    /** 把元素写进向量索引（best-effort，失败只记日志，不回滚业务）。 */
    private void indexUpsert(FrameworkElement el) {
        if (vectorIndexes == null) return;
        com.aiwriter.ai.rag.VectorIndex idx = vectorIndexes.getIfAvailable();
        if (idx != null) idx.upsert(el);
    }

    /** 元素被弃用后，从索引里删掉，避免以后检索到已废弃的设定。 */
    private void indexDelete(String elementId) {
        if (vectorIndexes == null) return;
        com.aiwriter.ai.rag.VectorIndex idx = vectorIndexes.getIfAvailable();
        if (idx != null) idx.delete(elementId);
    }

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
        FrameworkElement saved = elementRepo.save(el);
        indexUpsert(saved);
        return toResponse(saved);
    }

    public ElementResponse update(Long userId, Long frameworkId, String elementId, FrameworkElementRequest req) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = requireElementInFramework(frameworkId, elementId);
        int newVersion = latest.getVersion() + 1;

        FrameworkElement el = FrameworkElement.builder()
                .id(elementId).version(newVersion).framework(latest.getFramework())
                .user(User.builder().id(userId).build())
                .type(req.getType()).name(req.getName()).content(req.getContent())
                .isDeprecated(false).build();
        FrameworkElement saved = elementRepo.save(el);
        indexUpsert(saved);
        return toResponse(saved);
    }

    public void delete(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = requireElementInFramework(frameworkId, elementId);
        latest.setIsDeprecated(true);
        elementRepo.save(latest);
        indexDelete(elementId);
    }

    /**
     * 全量重建某个框架的向量索引。
     *
     * <p>为什么需要它：索引是 best-effort 同步的（写库成功但索引失败不回滚），
     * 换 embedding 模型、改维度、或者向量库从没接过时，都要能一次性补齐。
     *
     * @return 写入条数；没有配置向量库时返回 0
     */
    public int reindex(Long userId, Long frameworkId) {
        frameworkService.findOwned(userId, frameworkId);
        if (vectorIndexes == null) return 0;
        com.aiwriter.ai.rag.VectorIndex idx = vectorIndexes.getIfAvailable();
        if (idx == null) return 0;
        return idx.reindex(frameworkId, elementRepo.findLatestByFrameworkId(frameworkId));
    }

    public List<ElementResponse> getVersions(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        // 先确认该元素确实属于这个（已校验归属的）框架，防止「我的框架 + 别人的元素 id」交叉越权
        requireElementInFramework(frameworkId, elementId);
        return elementRepo.findVersionsByElementId(elementId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * 取元素最新版本，并强制校验其所属框架 == 传入的 frameworkId。
     * 用于堵住交叉越权：攻击者用「自己的 frameworkId」+「他人的 elementId」绕过框架归属校验。
     */
    private FrameworkElement requireElementInFramework(Long frameworkId, String elementId) {
        FrameworkElement latest = elementRepo.findLatestByElementId(elementId)
                .orElseThrow(() -> new NotFoundException("元素不存在"));
        if (latest.getFramework() == null || !frameworkId.equals(latest.getFramework().getId())) {
            throw new ForbiddenException("该元素不属于指定框架");
        }
        return latest;
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

        String response = aiProvider.forUser(userId).complete(systemPrompt, userPrompt);
        return parseGeneratedElements(fw, userId, req.getType(), response);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 模型经常把 JSON 包在 ```json 代码块里，或前后加一句解释。
     * 直接 readValue 会失败，然后整段原文被当成一个元素的 content 存下来——
     * 界面上就会出现一条名字叫「AI 生成」、内容是一坨 JSON 的脏数据。
     */
    private String extractJsonArray(String raw) {
        if (raw == null) return "[]";
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) return raw;
        return raw.substring(start, end + 1);
    }

    private List<ElementResponse> parseGeneratedElements(Framework fw, Long userId, String type, String response) {
        try {
            var list = objectMapper.readValue(extractJsonArray(response), List.class);
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

