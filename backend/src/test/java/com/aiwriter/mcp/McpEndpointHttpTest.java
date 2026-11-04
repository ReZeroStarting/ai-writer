package com.aiwriter.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MCP 端点的 HTTP 层冒烟测试（standalone MockMvc，不起 Spring 容器）。
 *
 * <p>单元测试保证"逻辑对"，这里保证"协议对"：状态码、Content-Type、
 * JSON-RPC 信封字段、通知不回 body。
 */
class McpEndpointHttpTest {

    private final McpToolRegistry registry = mock(McpToolRegistry.class);
    private final McpServerController controller = new McpServerController(registry);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    @DisplayName("initialize 走 HTTP：200 + application/json + 正确的 JSON-RPC 信封")
    void initializeOverHttp() throws Exception {
        when(registry.serverName()).thenReturn("ai-writer");
        when(registry.serverVersion()).thenReturn("0.1.0");

        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize",
                                 "params":{"protocolVersion":"2025-06-18"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("ai-writer"));
    }

    @Test
    @DisplayName("tools/list 走 HTTP：返回工具数组")
    void listsToolsOverHttp() throws Exception {
        when(registry.tools()).thenReturn(List.of(
                Map.of("name", "list_projects", "description", "列出项目",
                        "inputSchema", Map.of("type", "object"))));

        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name").value("list_projects"));
    }

    @Test
    @DisplayName("未带令牌调用工具：按协议返回错误码，而不是 500")
    void toolsCallWithoutAuth() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                                 "params":{"name":"list_projects","arguments":{}}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32001));
    }

    @Test
    @DisplayName("通知类消息：202 且响应体为空")
    void notificationHasNoBody() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString()).isEmpty());
    }
}
