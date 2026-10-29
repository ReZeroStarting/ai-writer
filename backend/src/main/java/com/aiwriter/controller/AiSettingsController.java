package com.aiwriter.controller;

import com.aiwriter.ai.AiPresets;
import com.aiwriter.ai.AiProvider;
import com.aiwriter.ai.AiProviderResolver;
import com.aiwriter.ai.SecretBox;
import com.aiwriter.dto.request.AiSettingRequest;
import com.aiwriter.dto.response.AiSettingResponse;
import com.aiwriter.model.AiSetting;
import com.aiwriter.repository.AiSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户自己的 AI 上游配置：选厂商、填自己的 key、随时切回服务端默认。
 *
 * <p>安全约定：
 * <ul>
 *   <li>key 只存密文 + 掩码，接口只返回掩码，明文既不回传也不写日志；</li>
 *   <li>userId 一律取自 token，不接受前端传；</li>
 *   <li>「测试连接」用当前用户解析出来的客户端真打一次上游。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/settings")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingRepository settings;
    private final AiProviderResolver resolver;

    @Value("${ai.key-secret:}")
    private String secret;

    /** 可选的上游清单（前端不硬编码厂商表）。 */
    @GetMapping("/providers")
    public ResponseEntity<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> list = AiPresets.all().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.id(),
                        "label", p.label(),
                        "protocol", p.protocol().name(),
                        "baseUrl", p.baseUrl(),
                        "defaultModel", p.defaultModel(),
                        "consoleUrl", p.consoleUrl(),
                        "note", p.note()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping
    public ResponseEntity<AiSettingResponse> get(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        AiSetting s = settings.findById(userId).orElse(null);
        return ResponseEntity.ok(toResponse(s));
    }

    @PutMapping
    public ResponseEntity<AiSettingResponse> save(Authentication auth, @RequestBody AiSettingRequest req) {
        Long userId = (Long) auth.getPrincipal();
        String provider = normalize(req.getProvider());
        AiSetting existing = settings.findById(userId).orElse(null);

        boolean clearing = provider == null;
        String key = req.getApiKey() == null ? "" : req.getApiKey().trim();

        if (clearing && key.isEmpty()) {
            // 没指定厂商也没给 key：等价于「用服务端默认」
            settings.deleteById(userId);
            return get(auth);
        }
        if (key.isEmpty() && (existing == null || existing.getApiKeyEnc() == null)) {
            throw new IllegalArgumentException("请填写 API Key");
        }
        // 提前校验配置是否可用，别等到写作时才报错
        String effectiveProvider = provider != null ? provider
                : (existing != null ? existing.getProvider() : null);
        String effectiveModel = isBlank(req.getModel()) ? (existing != null ? existing.getModel() : null) : req.getModel();
        String effectiveBaseUrl = isBlank(req.getBaseUrl()) ? (existing != null ? existing.getBaseUrl() : null) : req.getBaseUrl();
        resolver.build(effectiveProvider, key.isEmpty() ? "placeholder-key" : key, effectiveModel, effectiveBaseUrl);

        AiSetting entity = existing != null ? existing : AiSetting.builder().userId(userId).build();
        entity.setProvider(effectiveProvider.toLowerCase());
        entity.setModel(trimToNull(req.getModel()));
        entity.setBaseUrl(trimToNull(req.getBaseUrl()));
        if (!key.isEmpty()) {
            entity.setApiKeyEnc(SecretBox.encrypt(key, secret));
            entity.setApiKeyHint(SecretBox.mask(key));
        }
        settings.save(entity);
        return get(auth);
    }

    @DeleteMapping
    public ResponseEntity<AiSettingResponse> clear(Authentication auth) {
        settings.deleteById((Long) auth.getPrincipal());
        return get(auth);
    }

    /** 用当前生效的配置真调一次上游，确认 key 能用。 */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        AiProvider provider = resolver.forUser(userId);
        try {
            String reply = provider.complete("你是一个连通性测试助手，只回复两个字符。", "请只回复：OK");
            String trimmed = reply == null ? "" : reply.trim();
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "provider", provider.providerName(),
                    "reply", trimmed.substring(0, Math.min(20, trimmed.length()))));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "provider", provider.providerName(),
                    "message", String.valueOf(e.getMessage())));
        }
    }

    private AiSettingResponse toResponse(AiSetting s) {
        String label = null;
        if (s != null && s.getProvider() != null) {
            AiPresets.Preset p = AiPresets.find(s.getProvider());
            label = p != null ? p.label() : "自定义";
        }
        return AiSettingResponse.builder()
                .provider(s == null ? null : s.getProvider())
                .providerLabel(label)
                .maskedKey(s == null ? null : s.getApiKeyHint())
                .model(s == null ? null : s.getModel())
                .baseUrl(s == null ? null : s.getBaseUrl())
                .hasKey(s != null && s.getApiKeyEnc() != null && !s.getApiKeyEnc().isBlank())
                .defaultProvider(resolver.defaultProviderName())
                .updatedAt(s == null || s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString())
                .build();
    }

    /** 允许预设 id 或 custom；其它一律拒绝。 */
    private static String normalize(String provider) {
        if (provider == null || provider.isBlank()) return null;
        String p = provider.trim().toLowerCase();
        if (AiPresets.isCustom(p) || AiPresets.find(p) != null) return p;
        throw new IllegalArgumentException("不支持的 AI 上游: " + provider);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
