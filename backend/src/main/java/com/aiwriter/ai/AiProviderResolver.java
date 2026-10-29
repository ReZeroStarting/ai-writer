package com.aiwriter.ai;

import com.aiwriter.model.AiSetting;
import com.aiwriter.repository.AiSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 按用户解析「这次调用该用哪个上游」。
 *
 * <p>规则只有一条：**用户自己配了 key 就用他的，没配就用服务端默认的**。
 * 用户那条记录解密失败（比如换了服务器密钥）时也回退到默认，并记一条日志——
 * 宁可让用户暂时用服务端的额度，也不要整个功能报错。
 *
 * <p>每次请求现建一个轻量客户端：WebClient 本身很轻，用户量在这个项目里只有个位数，
 * 加缓存反而要处理「改了 key 没生效」的困惑。
 */
@Service
public class AiProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(AiProviderResolver.class);

    private final AiProvider serverDefault;
    private final AiSettingRepository settings;
    private final String secret;

    public AiProviderResolver(AiProvider serverDefault,
                              AiSettingRepository settings,
                              @Value("${ai.key-secret:}") String secret) {
        this.serverDefault = serverDefault;
        this.settings = settings;
        this.secret = secret;
    }

    /** 服务端默认上游的名字，用于界面上显示「不配置时用哪个」。 */
    public String defaultProviderName() {
        return serverDefault.providerName();
    }

    public AiProvider forUser(Long userId) {
        if (userId == null) return serverDefault;
        AiSetting s = settings.findById(userId).orElse(null);
        if (s == null || s.getApiKeyEnc() == null || s.getApiKeyEnc().isBlank()) return serverDefault;

        String key;
        try {
            key = SecretBox.decrypt(s.getApiKeyEnc(), secret);
        } catch (Exception e) {
            log.warn("用户 {} 的 API Key 解密失败，回退服务端默认: {}", userId, e.getMessage());
            return serverDefault;
        }
        if (key == null || key.isBlank()) return serverDefault;
        return build(s.getProvider(), key, s.getModel(), s.getBaseUrl());
    }

    /** 用指定的配置直接建一个客户端（「测试连接」用）。 */
    public AiProvider build(String provider, String apiKey, String model, String baseUrl) {
        if ("ANTHROPIC".equalsIgnoreCase(provider)) {
            return new ClaudeService(apiKey,
                    blankTo(baseUrl, "https://api.anthropic.com"),
                    blankTo(model, "claude-sonnet-4-6"), 0.7, 4096);
        }
        return new DeepSeekService(apiKey,
                blankTo(baseUrl, "https://api.deepseek.com"),
                blankTo(model, "deepseek-flash"), 0.7, 4096);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
