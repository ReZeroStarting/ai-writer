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

    public String buildAutoWriteSystem(String s) {
        return wrapStyle("你是一个专业小说作家。请根据用户提供的主题和设定，创作一篇完整的短篇小说或章节。" +
                "包含生动的场景描写、人物对话和情节推进。字数在1000-3000字之间。", s);
    }

    public String buildImitateSystem(String s) {
        return wrapStyle("你是一个精通模仿写作的专业作家。请仔细分析用户提供的参考文本的写作风格（句式特征、用词偏好、" +
                "叙事节奏、情绪基调），然后用相同的风格，根据用户指定的主题进行创作。" +
                "务必保持参考文本的神韵和笔触，而不是简单套用模板。", s);
    }
}
