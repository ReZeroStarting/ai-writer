package com.aiwriter.ai;

import reactor.core.publisher.Flux;

/**
 * 下游 SSE 文本编码。
 *
 * <p>Spring MVC 返回 {@code Flux<String>} + {@code text/event-stream} 时，会把**每个元素**
 * 按行写成 SSE 事件，具体行为（实测）：
 *
 * <pre>
 * 元素 "庙里"      → "data:庙里\n\n"
 * 元素 "第一行\n第二行" → "data:第一行\ndata:第二行\n\n"   ← 换行由 Spring 自己拆行
 * </pre>
 *
 * <p>由此有两条必须遵守的规则：
 * <ol>
 *   <li><b>不要自己拼 "data:" 前缀</b>——Spring 已经拼了，手拼会得到 "data:data: ..."；</li>
 *   <li>SSE 解码端会去掉冒号后的**一个**空格，而 Spring 不替行首空格做保护，
 *       于是正文里以空格开头的行（英文分词处）会被吃掉一个空格，"Hello" + " world"
 *       粘成 "Helloworld"。这里给每行补一个空格，正好被解码端吃掉，原文得以还原。</li>
 * </ol>
 */
public final class SseText {

    private SseText() {
    }

    /** 对整条流逐个元素做 SSE 安全化。 */
    public static Flux<String> escapeAll(Flux<String> chunks) {
        return chunks.map(SseText::escape);
    }

    /** 给每一行补一个「保护空格」，供 SSE 解码时被消耗掉。 */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        StringBuilder sb = new StringBuilder(normalized.length() + 8);
        sb.append(' ');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            sb.append(c);
            if (c == '\n') {
                // 换行是行边界：下一行开头同样需要保护空格（含行尾换行后的空行）
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
