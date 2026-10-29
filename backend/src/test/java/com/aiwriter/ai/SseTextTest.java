package com.aiwriter.ai;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseText 单元测试。
 *
 * <p>锁定的回归：
 * <ul>
 *   <li>Spring MVC 自己会按行拆 SSE 帧，控制器**不能**再拼 "data:" 前缀（否则双前缀）；</li>
 *   <li>SSE 解码端会吃掉冒号后的一个空格，而 Spring 不加保护，正文行首空格会丢
 *       （英文 "Hello" + " world" 粘成 "Helloworld"），所以每行要补一个空格。</li>
 * </ul>
 *
 * <p>断言里用 {@link #decode} 模拟客户端解码：按行去掉一个前导空格后用 \n 拼回。
 */
class SseTextTest {

    /** 模拟前端按 SSE 规范解码 Spring 写出的帧。 */
    private static String decode(String escaped) {
        String[] lines = escaped.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(" ")) lines[i] = lines[i].substring(1);
        }
        return String.join("\n", lines);
    }

    @Test
    void shouldRoundTripPlainText() {
        assertEquals("庙里住着一个老和尚", decode(SseText.escape("庙里住着一个老和尚")));
    }

    @Test
    void shouldRoundTripMultiLineText() {
        String text = "第一行\n第二行\n第三行";
        assertEquals(text, decode(SseText.escape(text)));
    }

    @Test
    void shouldRoundTripWhitespaceOnlyChunks() {
        // 早期担心过：元素是 "\n" 时换行会整个丢失
        assertEquals("\n", decode(SseText.escape("\n")));
        assertEquals("\n\n", decode(SseText.escape("\n\n")));
    }

    @Test
    void shouldRoundTripLeadingSpacesOnEveryLine() {
        // 英文分词：token 常以空格开头
        assertEquals(" world", decode(SseText.escape(" world")));
        assertEquals("a\n b", decode(SseText.escape("a\n b")));
        assertEquals("Hello ", decode(SseText.escape("Hello ")));
    }

    @Test
    void shouldRoundTripTrailingNewline() {
        String text = "段落结束。\n";
        assertEquals(text, decode(SseText.escape(text)));
    }

    @Test
    void shouldNormalizeCrlf() {
        assertEquals("甲\n乙", decode(SseText.escape("甲\r\n乙")));
        assertEquals("甲\n乙", decode(SseText.escape("甲\r乙")));
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertEquals("", SseText.escape(null));
        assertEquals("", SseText.escape(""));
    }

    @Test
    void escapeAllShouldMapWholeStream() {
        List<String> out = SseText.escapeAll(Flux.just("段落一\n", "段落二")).collectList().block();
        assertEquals(List.of(" 段落一\n ", " 段落二"), out);
    }
}
