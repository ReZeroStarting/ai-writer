package com.aiwriter.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * 模型输出的 JSON 守卫。
 *
 * <p>大模型"说好只输出 JSON"并不可靠：常见偏差有
 * <ul>
 *   <li>用 ```json 围栏包一层；</li>
 *   <li>前面加一句"好的，以下是结果："；</li>
 *   <li>尾部补一段解释。</li>
 * </ul>
 *
 * <p>所以这里不做"信任解析"，而是**先定位再解析**：剥掉围栏、取第一个
 * <code>[</code> 到最后一个 <code>]</code>，解析失败一律返回空——宁可少报，
 * 也不要让一个解析异常打断主流程。
 */
public final class JsonGuard {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonGuard() {
    }

    /** 从模型原始输出里提取 JSON 数组；失败返回 {@link Optional#empty()}。 */
    public static Optional<JsonNode> extractArray(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String text = stripFences(raw).trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return Optional.empty();
        try {
            JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
            return node != null && node.isArray() ? Optional.of(node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 剥掉 Markdown 代码围栏（```json ... ```）。 */
    public static String stripFences(String raw) {
        String text = raw.trim();
        if (!text.startsWith("```")) return text;
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) return text;
        text = text.substring(firstNewline + 1);
        int closing = text.lastIndexOf("```");
        if (closing >= 0) text = text.substring(0, closing);
        return text.trim();
    }
}
