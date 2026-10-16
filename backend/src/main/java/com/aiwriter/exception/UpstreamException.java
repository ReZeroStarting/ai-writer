package com.aiwriter.exception;

/**
 * AI 上游服务调用失败（Anthropic 返回错误 / 网络不可达 / Key 无效等）。
 * 与「服务器内部错误」区分开：这是网关侧的可预期失败，返回 502 + 可读原因。
 */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message) {
        super(message);
    }
}
