package com.aiwriter.mcp;

import com.aiwriter.ai.rag.HashingEmbeddingModel;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.service.ConflictService;
import com.aiwriter.service.ElementService;
import com.aiwriter.service.FrameworkService;
import com.aiwriter.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolRegistryTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final FrameworkService frameworkService = mock(FrameworkService.class);
    private final ElementService elementService = mock(ElementService.class);
    private final ConflictService conflictService = mock(ConflictService.class);

    private final McpToolRegistry registry = new McpToolRegistry(
            projectService, frameworkService, elementService, conflictService, new HashingEmbeddingModel());

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("工具清单包含五个只读工具，且每个都有 inputSchema")
    void exposesReadOnlyTools() {
        var tools = registry.tools();

        assertThat(tools).hasSize(5);
        assertThat(tools).extracting(t -> t.get("name"))
                .containsExactly("list_projects", "get_framework", "list_framework_elements",
                        "find_conflicts", "rank_elements");
        assertThat(tools).allSatisfy(t -> assertThat(t).containsKeys("name", "description", "inputSchema"));
    }

    @Test
    @DisplayName("list_projects：把项目列表序列化成 JSON 文本")
    void listsProjects() throws Exception {
        when(projectService.list(anyLong())).thenReturn(List.of(
                ProjectResponse.builder().id(1L).name("长安十二时辰").build()));

        String text = registry.call("list_projects", mapper.createObjectNode(), 7L);

        assertThat(text).contains("长安十二时辰");
    }

    @Test
    @DisplayName("缺少必填参数时抛 IllegalArgumentException（上层转 -32602）")
    void requiresArguments() {
        assertThatThrownBy(() -> registry.call("find_conflicts", mapper.createObjectNode(), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frameworkId");
    }

    @Test
    @DisplayName("未知工具直接抛错，不静默成功")
    void rejectsUnknownTool() {
        assertThatThrownBy(() -> registry.call("delete_everything", mapper.createObjectNode(), 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("没有冲突时返回人类可读提示，而不是空数组")
    void reportsNoConflicts() {
        when(conflictService.listByFramework(anyLong(), anyLong())).thenReturn(List.of());

        String text = registry.call("find_conflicts", mapper.createObjectNode().put("frameworkId", 3), 7L);

        assertThat(text).contains("没有待处理冲突");
    }

    @Test
    @DisplayName("rank_elements：按查询相关性返回，最相关的排第一")
    void ranksElements() {
        when(elementService.list(anyLong(), anyLong(), any())).thenReturn(List.of(
                ElementResponse.builder().id("e1").type("location").name("长安城")
                        .content("唐朝都城，城门由禁军守卫").version(1).build(),
                ElementResponse.builder().id("e2").type("item").name("锅具")
                        .content("厨房用的锅").version(1).build()));

        String text = registry.call("rank_elements",
                mapper.createObjectNode().put("frameworkId", 1).put("query", "长安城 城门"), 7L);

        assertThat(text).contains("长安城");
        assertThat(text.indexOf("长安城")).isLessThan(text.indexOf("锅具"));
    }

    @Test
    @DisplayName("get_framework：项目不是框架模式时给明确提示")
    void reportsNonFrameworkProject() {
        when(frameworkService.findByProjectOrNull(anyLong(), eq(9L))).thenReturn(null);

        String text = registry.call("get_framework", mapper.createObjectNode().put("projectId", 9), 7L);

        assertThat(text).contains("不是框架模式");
    }
}
