package com.aiwriter.ai.metrics;

/**
 * 一次调用的 token 用量。
 *
 * @param input     输入 token
 * @param output    输出 token
 * @param estimated true 表示上游没返回 usage，这里是按字符数折算的估算值
 */
public record TokenUsage(long input, long output, boolean estimated) {

    /** 上游没给 usage 时的兜底折算：中文约 1 字 ≈ 1 token，英文约 3.2 字符 ≈ 1 token，取折中。 */
    public static long estimateFromChars(int chars) {
        if (chars <= 0) return 0;
        return Math.max(1, Math.round(chars / 3.2));
    }
}
