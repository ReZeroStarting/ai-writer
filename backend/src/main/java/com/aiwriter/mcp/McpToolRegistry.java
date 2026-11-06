package com.aiwriter.mcp;

import com.aiwriter.ai.rag.EmbeddingModel;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.service.ConflictService;
import com.aiwriter.service.ElementService;
import com.aiwriter.service.FrameworkService;
import com.aiwriter.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把写作系统的能力暴露成 MCP 工具，供 Claude Code / Cursor 这类客户端直接调用。
 *
 * <p>为什么值得做：写作者的真实工作流是「在编辑器里写 + 在 AI 助手里查设定」。
 * 有了 MCP，助手可以直接问"这个框架里关于沈砚的设定有哪些""最近有哪些冲突待处理"，
 * 而不需要用户复制粘贴——**这比在应用里再加一个聊天框有用**。
 *
 * <p>边界：这里只暴露**只读**能力。写操作（续写、改章节）故意不开放——
 * 让外部 Agent 直接改正文的风险，远大于它带来的便利。
 */
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private static final String SERVER_NAME = "ai-writer";
    private static final String SERVER_VERSION = "0.1.0";

    private final ProjectService projectService;
    private final FrameworkService frameworkService;
    private final ElementService elementService;
    private final ConflictService conflictService;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public String serverName() {
        return SERVER_NAME;
    }

    public String serverVersion() {
        return SERVER_VERSION;
    }

    /** MCP tools/list 的返回体。 */
    public List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("list_projects", "列出当前用户的写作项目（含 id 与名称）",
                Map.of()));
        tools.add(tool("get_framework", "按项目 id 取框架模式信息（框架 id、状态）",
                Map.of("projectId", Map.of("type", "integer", "description", "项目 id"))));
        tools.add(tool("list_framework_elements", "列出框架下的设定元素（角色/地点/物品等）",
                Map.of(
                        "frameworkId", Map.of("type", "integer", "description", "框架 id"),
                        "type", Map.of("type", "string", "description", "可选，按类型过滤"))));
        tools.add(tool("find_conflicts", "列出该框架下待处理的设定冲突（AI 审校结果）",
                Map.of("frameworkId", Map.of("type", "integer", "description", "框架 id"))));
        tools.add(tool("rank_elements", "按与给定正文的相关性检索设定元素（RAG）",
                Map.of(
                        "frameworkId", Map.of("type", "integer", "description", "框架 id"),
                        "query", Map.of("type", "string", "description", "当前正文或查询语句"),
                        "topK", Map.of("type", "integer", "description", "可选，默认 5"))));
        return tools;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", properties.keySet().stream().toList());
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", schema);
        return t;
    }

    /**
     * 执行工具，返回给模型看的纯文本。
     *
     * @throws IllegalArgumentException 未知工具或参数缺失（由上层映射成 JSON-RPC 错误）
     */
    public String call(String name, JsonNode args, Long userId) {
        JsonNode a = args == null ? mapper.createObjectNode() : args;
        return switch (name) {
            case "list_projects" -> json(projectService.list(userId));
            case "get_framework" -> {
                long projectId = requireLong(a, "projectId");
                FrameworkResponse fw = frameworkService.findByProjectOrNull(userId, projectId);
                yield fw == null ? "该项目还不是框架模式" : json(fw);
            }
            case "list_framework_elements" -> {
                long frameworkId = requireLong(a, "frameworkId");
                String type = a.hasNonNull("type") ? a.get("type").asText() : null;
                yield json(elementService.list(userId, frameworkId, type));
            }
            case "find_conflicts" -> {
                long frameworkId = requireLong(a, "frameworkId");
                List<ConflictResponse> conflicts = conflictService.listByFramework(userId, frameworkId);
                yield conflicts.isEmpty() ? "当前没有待处理冲突" : json(conflicts);
            }
            case "rank_elements" -> {
                long frameworkId = requireLong(a, "frameworkId");
                String query = a.path("query").asText("");
                int topK = a.hasNonNull("topK") ? a.get("topK").asInt(5) : 5;
                yield rankElements(userId, frameworkId, query, Math.max(1, Math.min(topK, 20)));
            }
            default -> throw new IllegalArgumentException("未知工具: " + name);
        };
    }

    /** RAG 工具的落地实现：与续写用的是同一个向量模型，保证"检索口径"一致。 */
    private String rankElements(Long userId, long frameworkId, String query, int topK) {
        // 统一走 ElementRanker：向量库（或内存余弦）+ 关键词混合检索，
        // 和续写链路保持同一口径（之前这里自己算了一遍余弦，导致两条路径不一致）
        List<ElementResponse> ranked = elementService.rankElements(userId, frameworkId, query, topK)
                .stream().map(elementService::toResponse).toList();
        if (ranked.isEmpty() || query.isBlank()) return "没有可检索的设定元素";
        List<Map<String, Object>> brief = ranked.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("type", e.getType());
            m.put("name", e.getName());
            m.put("version", e.getVersion());
            return m;
        }).toList();
        return json(brief);
    }

    private double cosine(float[] query, ElementResponse e) {
        float[] v = embeddingModel.embed(
                (e.getType() == null ? "" : e.getType()) + " "
                        + (e.getName() == null ? "" : e.getName()) + " "
                        + (e.getContent() == null ? "" : e.getContent()));
        int len = Math.min(query.length, v.length);
        double sum = 0;
        for (int i = 0; i < len; i++) sum += query[i] * v[i];
        return sum;
    }

    private long requireLong(JsonNode args, String field) {
        if (!args.hasNonNull(field)) throw new IllegalArgumentException("缺少参数: " + field);
        return args.get(field).asLong();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

