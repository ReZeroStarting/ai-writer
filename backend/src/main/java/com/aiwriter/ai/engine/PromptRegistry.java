package com.aiwriter.ai.engine;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 提示词注册表：把 system prompt 从代码里收拢成**带版本号的数据**。
 *
 * <p>动机：提示词是 AI 应用里改动最频繁的"代码"。散落在各个 service 里时，
 * 改一版没法追溯"这句效果变差是不是因为改了提示词"。给每条提示词一个
 * {@code id + version} 后，至少能做到：
 * <ul>
 *   <li>日志/指标里能带上用的哪一版；</li>
 *   <li>做 A/B 时按 id 取不同版本；</li>
 *   <li>评审时 diff 的是同一份文本，而不是散在方法里的字符串。</li>
 * </ul>
 *
 * <p>约定：**改内容必须升版本号**，不允许原地改。
 */
@Component
public class PromptRegistry {

    /** 一条提示词：id 稳定、version 递增、system 是正文。 */
    public record Prompt(String id, int version, String system) {
    }

    private final Map<String, Prompt> prompts = new LinkedHashMap<>();

    public PromptRegistry() {
        register("outline", 1,
                "你是一个专业写作助手。请根据用户提供的主题，生成详细大纲。"
                        + "包含核心论点、章节划分（3-7章）、每章核心内容。使用层级编号。");
        register("continue", 1,
                "你是一个专业写作助手。请续写用户提供的内容，保持一致的文风、语气和叙事节奏。");
        register("polish", 1,
                "你是一个专业润色助手。请改写优化用户提供的文本，提升表达质量。保持原意不变。");
        register("expand", 1,
                "你是一个专业写作助手。请扩展用户提供的内容，增加细节描写和论证，使内容更丰富饱满。");
        register("shorten", 1,
                "你是一个专业编辑。请精简用户提供的内容，删除冗余，保留核心信息和精华表达。");
        register("auto-write", 1,
                "你是一个专业小说作家。请根据用户提供的主题和设定，创作一篇完整的短篇小说或章节。"
                        + "包含生动的场景描写、人物对话和情节推进。字数在1000-3000字之间。");
        register("imitate", 1,
                "你是一个精通模仿写作的专业作家。请仔细分析用户提供的参考文本的写作风格（句式特征、用词偏好、"
                        + "叙事节奏、情绪基调），然后用相同的风格，根据用户指定的主题进行创作。"
                        + "务必保持参考文本的神韵和笔触，而不是简单套用模板。");
    }

    private void register(String id, int version, String system) {
        prompts.put(id, new Prompt(id, version, system));
    }

    public Optional<Prompt> find(String id) {
        return Optional.ofNullable(prompts.get(id));
    }

    /** 取提示词正文；id 不存在属于编码错误，直接抛，避免静默用空提示词。 */
    public String system(String id) {
        Prompt p = prompts.get(id);
        if (p == null) throw new IllegalArgumentException("未注册的提示词: " + id);
        return p.system();
    }

    public int version(String id) {
        Prompt p = prompts.get(id);
        if (p == null) throw new IllegalArgumentException("未注册的提示词: " + id);
        return p.version();
    }

    public Collection<Prompt> all() {
        return prompts.values();
    }
}
