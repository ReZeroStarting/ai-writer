package com.aiwriter.ai;

import com.aiwriter.ai.engine.PromptRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把「写作操作」映射到「提示词 + 风格后缀」。
 *
 * <p>提示词正文已收拢到 {@link PromptRegistry}（带版本号），这里只负责
 * 取文本并拼上用户选中的风格。改提示词请改注册表并升版本，不要改这里。
 */
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final PromptRegistry registry;

    public String buildOutlineSystem() {
        return registry.system("outline");
    }

    public String buildContinueSystem(String style) {
        return wrapStyle(registry.system("continue"), style);
    }

    public String buildPolishSystem(String style) {
        return wrapStyle(registry.system("polish"), style);
    }

    public String buildExpandSystem(String style) {
        return wrapStyle(registry.system("expand"), style);
    }

    public String buildShortenSystem(String style) {
        return wrapStyle(registry.system("shorten"), style);
    }

    public String buildAutoWriteSystem(String style) {
        return wrapStyle(registry.system("auto-write"), style);
    }

    public String buildImitateSystem(String style) {
        return wrapStyle(registry.system("imitate"), style);
    }

    /** 当前使用的提示词版本，便于日志/排查对齐"这次效果差是哪一版"。 */
    public int versionOf(String id) {
        return registry.version(id);
    }

    private String wrapStyle(String base, String style) {
        if (style != null && !style.isEmpty())
            return base + "\n\n写作风格要求：\n" + style;
        return base;
    }
}
