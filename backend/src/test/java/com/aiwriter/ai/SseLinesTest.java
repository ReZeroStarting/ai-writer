package com.aiwriter.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseLines 单元测试。
 *
 * 锁定的核心回归：WebClient 在 text/event-stream 下有两条解码路径，
 * 「data: 」前缀可能被剥掉也可能保留。早期实现写死
 * {@code filter(line -> line.startsWith("data: "))}，
 * 在前缀已被剥掉时会把**全部内容滤光**，症状是「200 + 零输出 + 无报错」。
 */
class SseLinesTest {

    /** 前缀被保留的形式（原始 SSE 行）。 */
    @Test
    void shouldStripDataPrefixWhenPresent() {
        assertEquals("{\"a\":1}", SseLines.stripDataPrefix("data: {\"a\":1}"));
        assertEquals("{\"a\":1}", SseLines.stripDataPrefix("data:{\"a\":1}"));
        assertEquals("{\"a\":1}", SseLines.stripDataPrefix("  data: {\"a\":1}  "));
    }

    /** 前缀已被 SSE 解码器剥掉的形式——必须原样保留，不能滤掉。 */
    @Test
    void shouldKeepPayloadWhenPrefixAlreadyStripped() {
        assertEquals("{\"a\":1}", SseLines.stripDataPrefix("{\"a\":1}"));
    }

    @Test
    void shouldHandleNullAndBlank() {
        assertEquals("", SseLines.stripDataPrefix(null));
        assertEquals("", SseLines.stripDataPrefix("   "));
    }

    @Test
    void payloadDetectionShouldAcceptJson() {
        assertTrue(SseLines.isPayload("{\"choices\":[]}"));
    }

    @Test
    void payloadDetectionShouldRejectNonPayloads() {
        assertFalse(SseLines.isPayload(""));
        assertFalse(SseLines.isPayload(null));
        assertFalse(SseLines.isPayload("[DONE]"));
        assertFalse(SseLines.isPayload("event: message_start"));
        assertFalse(SseLines.isPayload("id: 42"));
        assertFalse(SseLines.isPayload("retry: 1000"));
    }

    /** 端到端：两种输入形式必须得到同一份载荷序列。 */
    @Test
    void bothWireFormsShouldYieldIdenticalPayloads() {
        String[] withPrefix = {"data: {\"n\":1}", "data: ", "", "data: {\"n\":2}", "data: [DONE]"};
        String[] withoutPrefix = {"{\"n\":1}", "", "", "{\"n\":2}", "[DONE]"};

        java.util.List<String> a = java.util.Arrays.stream(withPrefix)
                .map(SseLines::stripDataPrefix).filter(SseLines::isPayload).toList();
        java.util.List<String> b = java.util.Arrays.stream(withoutPrefix)
                .map(SseLines::stripDataPrefix).filter(SseLines::isPayload).toList();

        assertEquals(a, b);
        assertEquals(2, a.size());
    }
}
