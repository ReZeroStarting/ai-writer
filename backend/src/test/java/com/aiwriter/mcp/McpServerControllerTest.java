package com.aiwriter.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpServerControllerTest {

    private final McpToolRegistry registry = mock(McpToolRegistry.class);
    private final McpServerController controller = new McpServerController(registry);
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode request(String method, int id) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        return node;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Object> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    @DisplayName("initialize：返回协议版本、能力集与服务信息")
    void initialize() {
        when(registry.serverName()).thenReturn("ai-writer");
        when(registry.serverVersion()).thenReturn("0.1.0");

        ObjectNode req = request("initialize", 1);
        ObjectNode params = req.putObject("params");
        params.put("protocolVersion", "2025-06-18");
        params.putObject("clientInfo").put("name", "cursor");

        Map<String, Object> body = body(controller.dispatch(req, 7L));
        Map<String, Object> result = (Map<String, Object>) body.get("result");

        assertThat(result.get("protocolVersion")).isEqualTo("2025-06-18");
        assertThat(result).containsKeys("capabilities", "serverInfo");
    }

    @Test
    @DisplayName("initialize：认识的协议版本按客户端的回，避免旧客户端握手失败")
    void negotiatesKnownProtocolVersion() {
        when(registry.serverName()).thenReturn("ai-writer");
        when(registry.serverVersion()).thenReturn("0.1.0");

        ObjectNode req = request("initialize", 2);
        req.putObject("params").put("protocolVersion", "2024-11-05");

        Map<String, Object> body = body(controller.dispatch(req, 7L));
        Map<String, Object> result = (Map<String, Object>) body.get("result");

        assertThat(result.get("protocolVersion")).isEqualTo("2024-11-05");
    }

    @Test
    @DisplayName("initialize：不认识的协议版本不能原样回显，回自己支持的默认版本")
    void rejectsUnknownProtocolVersion() {
        when(registry.serverName()).thenReturn("ai-writer");
        when(registry.serverVersion()).thenReturn("0.1.0");

        ObjectNode req = request("initialize", 3);
        req.putObject("params").put("protocolVersion", "1999-01-01");

        Map<String, Object> body = body(controller.dispatch(req, 7L));
        Map<String, Object> result = (Map<String, Object>) body.get("result");

        assertThat(result.get("protocolVersion")).isEqualTo("2025-06-18");
    }

    @Test
    @DisplayName("notifications/initialized：按协议返回 202 且无 body")
    void notificationReturns202() {
        ResponseEntity<Object> response = controller.dispatch(request("notifications/initialized", 1), 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("tools/list：返回注册的工具清单")
    void listsTools() {
        when(registry.tools()).thenReturn(List.of(Map.of("name", "list_projects")));

        Map<String, Object> body = body(controller.dispatch(request("tools/list", 2), 7L));
        Map<String, Object> result = (Map<String, Object>) body.get("result");

        assertThat(result.get("tools")).asList().hasSize(1);
    }

    @Test
    @DisplayName("tools/call 成功：包成 MCP 的 content 数组")
    void callsTool() {
        when(registry.call(eq("list_projects"), any(), anyLong())).thenReturn("[]");

        ObjectNode req = request("tools/call", 3);
        ObjectNode params = req.putObject("params");
        params.put("name", "list_projects");
        params.putObject("arguments");

        Map<String, Object> body = body(controller.dispatch(req, 7L));
        Map<String, Object> result = (Map<String, Object>) body.get("result");

        assertThat(result.get("isError")).isEqualTo(false);
        assertThat(result.get("content")).asList().hasSize(1);
    }

    @Test
    @DisplayName("未知工具：返回 JSON-RPC invalid params，而不是 500")
    void unknownToolReturnsError() {
        when(registry.call(any(), any(), anyLong())).thenThrow(new IllegalArgumentException("未知工具: x"));

        ObjectNode req = request("tools/call", 4);
        ObjectNode params = req.putObject("params");
        params.put("name", "x");
        params.putObject("arguments");

        Map<String, Object> body = body(controller.dispatch(req, 7L));
        Map<String, Object> error = (Map<String, Object>) body.get("error");

        assertThat(error.get("code")).isEqualTo(-32602);
    }

    @Test
    @DisplayName("未认证调用工具：返回 -32001，不执行")
    void requiresAuth() {
        ObjectNode req = request("tools/call", 5);
        req.putObject("params").put("name", "list_projects");

        Map<String, Object> body = body(controller.dispatch(req, null));
        Map<String, Object> error = (Map<String, Object>) body.get("error");

        assertThat(error.get("code")).isEqualTo(-32001);
    }

    @Test
    @DisplayName("未知方法：返回 -32601")
    void unknownMethod() {
        Map<String, Object> body = body(controller.dispatch(request("resources/list", 6), 7L));
        Map<String, Object> error = (Map<String, Object>) body.get("error");

        assertThat(error.get("code")).isEqualTo(-32601);
    }

    @Test
    @DisplayName("缺少 method 的请求：400")
    void rejectsMalformedRequest() {
        ObjectNode bad = mapper.createObjectNode();
        bad.put("id", 1);

        assertThat(controller.dispatch(bad, 7L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
