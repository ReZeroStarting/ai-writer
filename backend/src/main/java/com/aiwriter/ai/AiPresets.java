package com.aiwriter.ai;

import java.util.List;

/**
 * 常见的 AI 上游预设。
 *
 * <p>市面上绝大多数厂商（OpenAI、DeepSeek、Kimi、智谱、通义、Gemini、Groq、OpenRouter…）
 * 都提供 **OpenAI 兼容的 /chat/completions**，所以这里不需要为每家写一个实现：
 * 一份 OpenAI 兼容客户端 + 一张「地址/默认模型」表就够了。
 * 只有 Anthropic 的 Messages API 协议不同，单独走 {@code ClaudeService}。
 *
 * <p>表里只放**能确认地址**的厂商；其余（自建、中转、新出的）走 {@code custom}，
 * 用户自己填 baseUrl 和模型即可。
 */
public final class AiPresets {

    public enum Protocol { OPENAI, ANTHROPIC }

    public record Preset(
            String id,
            String label,
            Protocol protocol,
            String baseUrl,
            String defaultModel,
            String consoleUrl,
            String note
    ) {
    }

    public static final String CUSTOM = "custom";

    private static final List<Preset> ALL = List.of(
            new Preset("openai", "OpenAI", Protocol.OPENAI,
                    "https://api.openai.com/v1", "gpt-4o-mini",
                    "https://platform.openai.com/api-keys", "官方直连；国内网络通常需要代理"),
            new Preset("anthropic", "Claude（Anthropic）", Protocol.ANTHROPIC,
                    "https://api.anthropic.com", "claude-sonnet-4-6",
                    "https://console.anthropic.com", "走 Anthropic Messages 协议"),
            new Preset("deepseek", "DeepSeek 深度求索", Protocol.OPENAI,
                    "https://api.deepseek.com", "deepseek-chat",
                    "https://platform.deepseek.com/api_keys", "价格低，中文写作常用"),
            new Preset("gemini", "Google Gemini", Protocol.OPENAI,
                    "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash",
                    "https://aistudio.google.com/apikey", "走 Google 的 OpenAI 兼容入口"),
            new Preset("dashscope", "通义千问（阿里云百炼）", Protocol.OPENAI,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                    "https://bailian.console.aliyun.com", "兼容模式，模型名如 qwen-plus / qwen-max"),
            new Preset("moonshot", "Kimi（月之暗面）", Protocol.OPENAI,
                    "https://api.moonshot.cn/v1", "moonshot-v1-8k",
                    "https://platform.moonshot.cn/console/api-keys", "长文本见长"),
            new Preset("zhipu", "智谱 GLM", Protocol.OPENAI,
                    "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash",
                    "https://bigmodel.cn/usercenter/apikeys", "glm-4-flash 免费额度较多"),
            new Preset("ark", "豆包（火山方舟）", Protocol.OPENAI,
                    "https://ark.cn-beijing.volces.com/api/v3", "doubao-1.5-pro-32k",
                    "https://console.volcengine.com/ark", "模型名要填方舟控制台里的接入点 ID"),
            new Preset("xai", "xAI Grok", Protocol.OPENAI,
                    "https://api.x.ai/v1", "grok-2-latest",
                    "https://console.x.ai", ""),
            new Preset("mistral", "Mistral AI", Protocol.OPENAI,
                    "https://api.mistral.ai/v1", "mistral-large-latest",
                    "https://console.mistral.ai/api-keys", ""),
            new Preset("groq", "Groq", Protocol.OPENAI,
                    "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
                    "https://console.groq.com/keys", "推理速度快，适合草稿"),
            new Preset("openrouter", "OpenRouter（聚合）", Protocol.OPENAI,
                    "https://openrouter.ai/api/v1", "anthropic/claude-3.5-sonnet",
                    "https://openrouter.ai/keys", "一个 key 调多家模型，模型名形如 vendor/model"),
            new Preset("siliconflow", "硅基流动 SiliconFlow", Protocol.OPENAI,
                    "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-72B-Instruct",
                    "https://cloud.siliconflow.cn/account/ak", "国内聚合，开源模型多"),
            new Preset("ollama", "本地 Ollama", Protocol.OPENAI,
                    "http://localhost:11434/v1", "qwen2.5:7b",
                    "https://ollama.com", "本地跑，Key 随便填（如 ollama）")
    );

    private AiPresets() {
    }

    public static List<Preset> all() {
        return ALL;
    }

    /** 找不到就返回 null，由调用方决定报错还是回退。 */
    public static Preset find(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase();
        return ALL.stream().filter(p -> p.id().equals(key)).findFirst().orElse(null);
    }

    public static boolean isCustom(String id) {
        return id != null && CUSTOM.equalsIgnoreCase(id.trim());
    }
}
