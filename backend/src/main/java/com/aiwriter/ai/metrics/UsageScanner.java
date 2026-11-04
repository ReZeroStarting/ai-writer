package com.aiwriter.ai.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从上游响应里扫 token 用量。
 *
 * <p>两套协议字段不同，但都能从"已解析的 JSON 分片"里拿到：
 * <ul>
 *   <li><b>Anthropic</b>：{@code message_start} 带 {@code message.usage.input_tokens}，
 *       {@code message_delta} 带 {@code usage.output_tokens}（累加式，取最后一次）；</li>
 *   <li><b>OpenAI 兼容</b>：非流式 {@code usage.prompt_tokens / completion_tokens}；
 *       流式需在请求里带 {@code stream_options.include_usage=true}，
 *       最后一个分片才带 usage。</li>
 * </ul>
 *
 * <p>拿不到 usage 时退回字符折算（{@link TokenUsage#estimateFromChars}），
 * 并标记 {@code estimated=true}，避免"估的"和"真的"混在一起看不出区别。
 */
public class UsageScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private long inputTokens;
    private long outputTokens;
    private int outputChars;

    /** 扫一个原始分片；解析失败静默忽略（不能因为算账把主链路弄挂）。 */
    public void scan(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;
        int start = rawJson.indexOf('{');
        if (start < 0) return;
        try {
            JsonNode node = MAPPER.readTree(rawJson.substring(start));
            // Anthropic
            JsonNode message = node.path("message");
            if (message.has("usage")) {
                readUsage(message.get("usage"), true);
            }
            if (node.has("usage")) {
                readUsage(node.get("usage"), true);
            }
        } catch (Exception ignored) {
            // 分片不是完整 JSON 或没有 usage，属于常态
        }
    }

    private void readUsage(JsonNode usage, boolean anthropicAware) {
        if (usage == null || usage.isMissingNode()) return;
        if (anthropicAware) {
            if (usage.has("input_tokens")) inputTokens = Math.max(inputTokens, usage.get("input_tokens").asLong());
            if (usage.has("output_tokens")) outputTokens = Math.max(outputTokens, usage.get("output_tokens").asLong());
        }
        if (usage.has("prompt_tokens")) inputTokens = Math.max(inputTokens, usage.get("prompt_tokens").asLong());
        if (usage.has("completion_tokens")) outputTokens = Math.max(outputTokens, usage.get("completion_tokens").asLong());
    }

    /** 记录本次真正推给前端的字符数，用于没有 usage 时的折算。 */
    public void addOutputChars(int chars) {
        if (chars > 0) outputChars += chars;
    }

    /**
     * 汇总本次用量：有真实 usage 就用真实值，否则按 prompt 字符数 + 输出字符数折算。
     */
    public TokenUsage toUsage(int promptChars) {
        if (inputTokens > 0 || outputTokens > 0) {
            return new TokenUsage(inputTokens, outputTokens, false);
        }
        return new TokenUsage(
                TokenUsage.estimateFromChars(promptChars),
                TokenUsage.estimateFromChars(outputChars),
                true);
    }
}
