package com.aiwriter.ai;

import reactor.core.publisher.Flux;

/**
 * AI 上游服务的统一抽象。
 *
 * <p>项目需要同时支持两家协议：
 * <ul>
 *   <li><b>Anthropic</b>（Claude）—— 自有 Messages API，<code>x-api-key</code> 鉴权，
 *       SSE 事件为 <code>content_block_delta</code></li>
 *   <li><b>DeepSeek</b> —— OpenAI 兼容协议，<code>Bearer</code> 鉴权，
 *       SSE 数据为 <code>choices[].delta.content</code></li>
 * </ul>
 *
 * <p>两者协议细节完全不同，但对上层（AiService / StyleAnalyzer）来说只需要
 * 「给一段 system + user，流式或一次性拿回文本」这一个能力。此接口就是那条缝。
 *
 * <p>具体装配哪一个实现由配置 <code>ai.provider</code> 决定，见各自的
 * <code>@ConditionalOnProperty</code>。
 */
public interface AiProvider {

    /**
     * 流式生成。
     *
     * <p>约定：上游失败时**不抛异常中断连接**，而是把可读的错误说明
     * 作为一条流式消息推给前端（用户才能区分是 key 无效、限流还是网络问题）。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 文本增量流
     */
    Flux<String> stream(String systemPrompt, String userPrompt);

    /**
     * 一次性生成（阻塞直到拿到完整结果）。
     *
     * <p>约定：上游失败时抛 {@link com.aiwriter.exception.UpstreamException}，
     * 由全局异常处理器映射为 502 并附带可读原因。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 完整生成文本
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * provider 标识，用于日志与健康检查。
     */
    String providerName();
}
