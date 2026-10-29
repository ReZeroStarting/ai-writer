package com.aiwriter.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把上游 AI 服务的异常翻译成用户可读的一句话说明。
 *
 * <p>两家 provider 都用同一套文案规则，但要把配置项名字（ANTHROPIC_API_KEY /
 * DEEPSEEK_API_KEY）写对，否则用户照着提示去查配置会找不到地方。
 */
final class UpstreamErrors {

    private static final Logger log = LoggerFactory.getLogger(UpstreamErrors.class);

    private UpstreamErrors() {
    }

    /**
     * @param err      上游异常
     * @param keyName  该 provider 对应的 API Key 环境变量名，用于 401/403 的提示文案
     * @param provider provider 标识，用于日志
     */
    static String describe(Throwable err, String keyName, String provider) {
        String msg = String.valueOf(err.getMessage());
        log.error("AI 上游调用失败[{}]: {}", provider, msg);

        if (msg.contains("401") || msg.contains("403")) {
            // 可能用的是用户自己在「AI 设置」里填的 key，也可能用的是服务端默认，两种都提示到`n            return "【AI 服务未授权】请检查你在「AI 设置」里填的 API Key 是否有效"`n                    + "（未配置时用的是服务端 " + keyName + "）。";
        }
        if (msg.contains("429")) {
            return "【AI 服务限流】请求过于频繁或额度不足，请稍后再试。";
        }
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
            return "【AI 服务暂时不可用】上游返回错误，请稍后再试。";
        }
        if (msg.contains("UnknownHost") || msg.contains("Connection") || msg.contains("timeout")) {
            return "【网络异常】无法连接 AI 服务，请检查网络或代理设置。";
        }
        if (msg.contains("insufficient") || msg.contains("balance") || msg.contains("余额")) {
            return "【AI 服务额度不足】账户余额不足，请充值后重试。";
        }
        return "【AI 调用失败】" + msg;
    }
}
