package com.aiwriter.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冲突检测的**回放式评测集**。
 *
 * <p>为什么要评测集：提示词改动是 AI 应用里最频繁的改动，没有基线就只能"感觉好像变好了"。
 * 这里把真实踩过的脏输出固化成用例，改提示词/改解析逻辑时跑一遍，
 * 命中数掉下来就说明回归了。
 *
 * <p>为什么用回放而不是真调模型：CI 里没有 API Key，也不该每次跑测试都花钱；
 * 真模型的效果评测应该是独立的、按需跑的（见 {@code docs/eval.md}）。
 */
class ConflictEvalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("评测集：10 个用例的解析结果全部符合预期")
    void allCasesPass() throws Exception {
        List<JsonNode> cases = loadCases();
        assertThat(cases).isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (JsonNode c : cases) {
            String name = c.path("name").asText();
            String output = c.path("modelOutput").asText();
            int expected = c.path("expectCount").asInt();
            Set<String> validIds = new HashSet<>();
            c.path("validIds").forEach(id -> validIds.add(id.asText()));

            int actual = ConflictFindingParser.parse(output, validIds).size();
            if (actual != expected) {
                failures.add(String.format("用例[%s] 期望 %d 条，实际 %d 条", name, expected, actual));
            }
        }

        assertThat(failures).as("评测失败用例").isEmpty();
    }

    @Test
    @DisplayName("解析出的条目字段完整，severity 缺省为 MINOR")
    void findingsAreWellFormed() {
        var findings = ConflictFindingParser.parse(
                "[{\"elementId\":\"e1\",\"description\":\"人设矛盾\"}]", Set.of("e1"));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo("MINOR");
        assertThat(findings.get(0).description()).isEqualTo("人设矛盾");
    }

    private List<JsonNode> loadCases() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/eval/conflict-cases.json")) {
            assertThat(in).as("评测集文件应存在").isNotNull();
            List<JsonNode> cases = new ArrayList<>();
            MAPPER.readTree(in).forEach(cases::add);
            return cases;
        }
    }
}
