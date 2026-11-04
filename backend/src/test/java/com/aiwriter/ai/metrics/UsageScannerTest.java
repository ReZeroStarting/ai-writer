package com.aiwriter.ai.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageScannerTest {

    @Test
    @DisplayName("Anthropic：message_start 的 input + message_delta 的 output 都能扫到")
    void scansAnthropicUsage() {
        UsageScanner scanner = new UsageScanner();
        scanner.scan("{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":120,\"output_tokens\":1}}}");
        scanner.scan("{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":340}}");

        TokenUsage usage = scanner.toUsage(999);

        assertThat(usage.estimated()).isFalse();
        assertThat(usage.input()).isEqualTo(120);
        assertThat(usage.output()).isEqualTo(340);
    }

    @Test
    @DisplayName("OpenAI 兼容：prompt_tokens / completion_tokens")
    void scansOpenAiUsage() {
        UsageScanner scanner = new UsageScanner();
        scanner.scan("{\"choices\":[],\"usage\":{\"prompt_tokens\":88,\"completion_tokens\":256}}");

        TokenUsage usage = scanner.toUsage(0);

        assertThat(usage.estimated()).isFalse();
        assertThat(usage.input()).isEqualTo(88);
        assertThat(usage.output()).isEqualTo(256);
    }

    @Test
    @DisplayName("上游没给 usage 时按字符折算，并标记 estimated")
    void fallsBackToEstimate() {
        UsageScanner scanner = new UsageScanner();
        scanner.addOutputChars(320);

        TokenUsage usage = scanner.toUsage(640);

        assertThat(usage.estimated()).isTrue();
        assertThat(usage.input()).isEqualTo(200);   // 640 / 3.2
        assertThat(usage.output()).isEqualTo(100);  // 320 / 3.2
    }

    @Test
    @DisplayName("分片不是 JSON 或没有 usage 时静默忽略，不抛异常")
    void ignoresGarbage() {
        UsageScanner scanner = new UsageScanner();
        scanner.scan(null);
        scanner.scan("");
        scanner.scan("[DONE]");
        scanner.scan("{\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}");

        assertThat(scanner.toUsage(0).estimated()).isTrue();
    }

    @Test
    @DisplayName("多次 message_start 取最大值，避免被后续分片覆盖成 0")
    void keepsMaxForInputTokens() {
        UsageScanner scanner = new UsageScanner();
        scanner.scan("{\"message\":{\"usage\":{\"input_tokens\":50}}}");
        scanner.scan("{\"message\":{\"usage\":{\"input_tokens\":0}}}");

        assertThat(scanner.toUsage(0).input()).isEqualTo(50);
    }
}
