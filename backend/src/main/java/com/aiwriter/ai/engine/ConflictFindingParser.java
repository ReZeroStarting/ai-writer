package com.aiwriter.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 把模型返回的"冲突检测结果"解析成结构化条目（纯函数，无 Spring 依赖）。
 *
 * <p>单独抽出来的原因：这是整条链路上**唯一需要跟模型输出打交道**的地方，
 * 也是最该被回归测试覆盖的地方。抽成纯函数后，评测集可以喂各种脏输出来跑，
 * 不需要起 Spring、更不需要真的调模型。
 *
 * <p>四条规则（都是线上踩过的）：
 * <ol>
 *   <li>只取 JSON 数组，围栏和前后解释由 {@link JsonGuard} 处理；</li>
 *   <li>最多 3 条——模型很容易"发现"一堆鸡毛蒜皮；</li>
 *   <li>elementId 必须指向真实存在的设定，模型编的 id 直接丢弃；</li>
 *   <li>description 为空的行丢弃，避免页面上出现空条目。</li>
 * </ol>
 */
public final class ConflictFindingParser {

    public record Finding(String elementId, String severity, String description) {
    }

    private static final int MAX_FINDINGS = 3;
    private static final int MAX_DESCRIPTION = 300;

    private ConflictFindingParser() {
    }

    public static List<Finding> parse(String raw, Set<String> validElementIds) {
        var arrayOpt = JsonGuard.extractArray(raw);
        if (arrayOpt.isEmpty()) return List.of();

        List<Finding> findings = new ArrayList<>();
        for (JsonNode node : arrayOpt.get()) {
            if (findings.size() >= MAX_FINDINGS) break;
            String id = node.path("elementId").asText("").trim();
            String description = node.path("description").asText("").trim();
            if (id.isEmpty() || description.isEmpty()) continue;
            if (validElementIds != null && !validElementIds.contains(id)) continue;
            findings.add(new Finding(id, node.path("severity").asText("MINOR"), abbreviate(description)));
        }
        return findings;
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION) + "…";
    }
}
