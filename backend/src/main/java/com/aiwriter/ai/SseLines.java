package com.aiwriter.ai;

/**
 * SSE 行处理的共享工具。
 *
 * <p>存在的理由是一个真实踩过的坑：用 {@code WebClient.bodyToFlux(String.class)}
 * 以 {@code text/event-stream} 请求上游时，Spring 会先尝试用
 * {@code ServerSentEventHttpMessageReader} 解码——它**只返回 data 字段的内容，
 * 已把 "data: " 前缀剥掉**。而一旦走了普通文本解码路径，前缀又会保留。
 *
 * <p>早期实现直接写死 {@code filter(line -> line.startsWith("data: "))}，
 * 在前缀被剥掉的情况下会把**全部内容滤光**，表现为「接口 200、零输出、无报错」——
 * 极难排查。因此这里对两种形式都做兼容。
 */
final class SseLines {

    private SseLines() {
    }

    /** 剥掉可能的 "data:" 前缀并去空白；前缀不存在时原样返回。 */
    static String stripDataPrefix(String line) {
        if (line == null) return "";
        String trimmed = line.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed.substring(5).trim();
        }
        return trimmed;
    }

    /** 判断剥前缀后的内容是否是需要解析的有效载荷。 */
    static boolean isPayload(String payload) {
        if (payload == null || payload.isEmpty()) return false;
        // SSE 的其他字段（event: / id: / retry:）与结束标记都不是载荷
        if (payload.startsWith("event:") || payload.startsWith("id:") || payload.startsWith("retry:")) {
            return false;
        }
        if ("[DONE]".equals(payload)) return false;
        // 载荷应为 JSON 对象
        return payload.startsWith("{");
    }
}
