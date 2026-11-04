package com.aiwriter.ai.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonGuardTest {

    @Test
    @DisplayName("代码围栏包裹的 JSON 数组能解析出来")
    void parsesFencedArray() {
        String raw = "```json\n[{\"elementId\":\"12\",\"severity\":\"MAJOR\",\"description\":\"设定是盲人\"}]\n```";
        var arr = JsonGuard.extractArray(raw);

        assertThat(arr).isPresent();
        assertThat(arr.get()).hasSize(1);
        assertThat(arr.get().get(0).path("elementId").asText()).isEqualTo("12");
    }

    @Test
    @DisplayName("前后带解释文字时，只取中间的数组")
    void parsesArrayWithSurroundingProse() {
        String raw = "好的，以下是检测结果：\n[{\"elementId\":\"7\",\"description\":\"地点冲突\"}]\n以上。";
        var arr = JsonGuard.extractArray(raw);

        assertThat(arr).isPresent();
        assertThat(arr.get().get(0).path("elementId").asText()).isEqualTo("7");
    }

    @Test
    @DisplayName("空数组是合法结果，表示没有冲突")
    void parsesEmptyArray() {
        var arr = JsonGuard.extractArray("[]");
        assertThat(arr).isPresent();
        assertThat(arr.get()).isEmpty();
    }

    @Test
    @DisplayName("解析不了就返回空，不抛异常")
    void returnsEmptyOnGarbage() {
        assertThat(JsonGuard.extractArray("模型今天不想输出 JSON")).isEmpty();
        assertThat(JsonGuard.extractArray("[{坏掉}]")).isEmpty();
        assertThat(JsonGuard.extractArray(null)).isEmpty();
        assertThat(JsonGuard.extractArray("  ")).isEmpty();
    }

    @Test
    @DisplayName("对象而不是数组时返回空")
    void rejectsObject() {
        assertThat(JsonGuard.extractArray("{\"elementId\":\"1\"}")).isEmpty();
    }

    @Test
    @DisplayName("剥围栏不影响普通文本")
    void stripLeavesPlainTextAlone() {
        assertThat(JsonGuard.stripFences("[1,2]")).isEqualTo("[1,2]");
    }
}
