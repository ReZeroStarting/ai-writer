package com.aiwriter.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildOutlineSystem() {
        return "你是一个专业写作助手。请根据用户提供的主题，生成详细大纲。" +
                "包含核心论点、章节划分（3-7章）、每章核心内容。使用层级编号。";
    }

    private String wrapStyle(String base, String style) {
        if (style != null && !style.isEmpty())
            return base + "\n\n写作风格要求：\n" + style;
        return base;
    }

    public String buildContinueSystem(String s) {
        return wrapStyle("你是一个专业写作助手。请续写用户提供的内容，保持一致的文风、语气和叙事节奏。", s);
    }
    public String buildPolishSystem(String s) {
        return wrapStyle("你是一个专业润色助手。请改写优化用户提供的文本，提升表达质量。保持原意不变。", s);
    }
    public String buildExpandSystem(String s) {
        return wrapStyle("你是一个专业写作助手。请扩展用户提供的内容，增加细节描写和论证，使内容更丰富饱满。", s);
    }
    public String buildShortenSystem(String s) {
        return wrapStyle("你是一个专业编辑。请精简用户提供的内容，删除冗余，保留核心信息和精华表达。", s);
    }
}
