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
 * <p>规则：**用户自己配了就用他的，没配或解不开就用服务端默认**。
 * 用户选的厂商由 {@link AiPresets} 决定走哪个协议：
 * Anthropic 走 Messages API，其余全部走同一份 OpenAI 兼容客户端。
 */
@Service
public class AiProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(AiProviderResolver.class);
    private static final String USER_KEY_HINT = "你在「AI 设置」里填的 Key";

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

    /** 界面展示用：当前用户是否在用自带 Key，以及用的哪家。 */
    public AiSetting settingOf(Long userId) {
        return userId == null ? null : settings.findById(userId).orElse(null);
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
        try {
            return build(s.getProvider(), key, s.getModel(), s.getBaseUrl());
        } catch (IllegalArgumentException e) {
            log.warn("用户 {} 的上游配置无效（{}），回退服务端默认", userId, e.getMessage());
            return serverDefault;
        }
    }

    /**
     * 按预设构造一个客户端。
     *
     * @param providerId 预设 id（openai / deepseek / moonshot…）或 {@code custom}
     */
    public AiProvider build(String providerId, String apiKey, String model, String baseUrl) {
        String id = providerId == null ? "" : providerId.trim().toLowerCase();

        if (AiPresets.isCustom(id)) {
            if (blank(baseUrl)) throw new IllegalArgumentException("自定义上游需要填写接口地址");
            if (blank(model)) throw new IllegalArgumentException("自定义上游需要填写模型名");
            return new OpenAiCompatibleService("custom", USER_KEY_HINT, apiKey, baseUrl.trim(), model.trim(), 0.7, 4096);
        }

        AiPresets.Preset preset = AiPresets.find(id);
        if (preset == null) throw new IllegalArgumentException("不支持的 AI 上游: " + providerId);

        String url = blank(baseUrl) ? preset.baseUrl() : baseUrl.trim();
        String m = blank(model) ? preset.defaultModel() : model.trim();

        if (preset.protocol() == AiPresets.Protocol.ANTHROPIC) {
            return new ClaudeService(apiKey, url, m, 0.7, 4096, USER_KEY_HINT);
        }
        return new OpenAiCompatibleService(preset.id(), USER_KEY_HINT, apiKey, url, m, 0.7, 4096);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
