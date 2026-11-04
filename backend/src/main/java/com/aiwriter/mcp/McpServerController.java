package com.aiwriter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP Server 端点：一个 JSON-RPC 2.0 的 POST 接口（Streamable HTTP 的 JSON 响应模式）。
 *
 * <p>支持的方法：
 * <ul>
 *   <li>{@code initialize} —— 握手，回能力集；</li>
 *   <li>{@code notifications/initialized} —— 通知，无响应体（202）；</li>
 *   <li>{@code tools/list} / {@code tools/call} —— 工具列表与调用；</li>
 *   <li>{@code ping} —— 保活。</li>
 * </ul>
 *
 * <p>鉴权：走既有的 JWT（{@code Authorization: Bearer <accessToken>}）。
 * MCP 客户端在配置里带上 header 即可；工具内部一律按当前用户做归属校验，
 * 拿到别人的 frameworkId 也读不到数据。
 */
@RestController
@RequiredArgsConstructor
public class McpServerController {

    /** 服务端支持的协议版本（协商时不回显不认识的版本，避免客户端按错误语义解析）。 */
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS =
            Set.of("2025-06-18", "2025-03-26", "2024-11-05");

    private final McpToolRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handle(@RequestBody JsonNode request, Authentication auth) {
        Long userId = auth != null && auth.getPrincipal() instanceof Long id ? id : null;
        return dispatch(request, userId);
    }

    /** 抽成独立方法便于单测：不依赖 MVC，也不用起容器。 */
    ResponseEntity<Object> dispatch(JsonNode request, Long userId) {
        if (request == null || !request.hasNonNull("method")) {
            return ResponseEntity.badRequest().body(error(null, -32600, "无效的 JSON-RPC 请求"));
        }
        String method = request.get("method").asText();
        JsonNode id = request.get("id");
        JsonNode params = request.path("params");

        // 通知类消息没有 id，按协议不回 body
        if (method.startsWith("notifications/")) {
            return ResponseEntity.accepted().build();
        }

        return switch (method) {
            case "initialize" -> ResponseEntity.ok(result(id, initializeResult(params)));
            case "ping" -> ResponseEntity.ok(result(id, Map.of()));
            case "tools/list" -> ResponseEntity.ok(result(id, Map.of("tools", registry.tools())));
            case "tools/call" -> toolsCall(id, params, userId);
            default -> ResponseEntity.ok(error(id, -32601, "不支持的方法: " + method));
        };
    }

    private Object initializeResult(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        // 认识的版本按客户端的回（兼容旧客户端）；不认识的回自己最新支持的，
        // 而不是原样回显——回一个自己实现不了的版本，会让客户端按错误语义解析后续响应。
        String protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
                ? requested : DEFAULT_PROTOCOL_VERSION;
        return Map.of(
                "protocolVersion", protocolVersion,
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of(
                        "name", registry.serverName(),
                        "version", registry.serverVersion()));
    }

    private ResponseEntity<Object> toolsCall(JsonNode id, JsonNode params, Long userId) {
        String name = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        if (name.isBlank()) {
            return ResponseEntity.ok(error(id, -32602, "缺少工具名"));
        }
        if (userId == null) {
            return ResponseEntity.ok(error(id, -32001, "未认证"));
        }
        try {
            String text = registry.call(name, arguments, userId);
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = result.putArray("content");
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
            return ResponseEntity.ok(result(id, result));
        } catch (IllegalArgumentException e) {
            // 参数错 / 未知工具：按 JSON-RPC 的 invalid params 返回，客户端能看懂
            return ResponseEntity.ok(error(id, -32602, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(error(id, -32603, "工具执行失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> result(JsonNode id, Object result) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) node.set("id", id);
        node.set("result", mapper.valueToTree(result));
        return mapper.convertValue(node, Map.class);
    }

    private Map<String, Object> error(JsonNode id, int code, String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.set("id", id == null ? mapper.nullNode() : id);
        ObjectNode err = node.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return mapper.convertValue(node, Map.class);
    }

    /** 工具清单（给调试页/测试用）。 */
    public List<Map<String, Object>> tools() {
        return registry.tools();
    }
}
