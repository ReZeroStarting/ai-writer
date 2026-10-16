package com.aiwriter.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StyleAnalyzer {
    private final AiProvider claude;

    public String analyzeStyle(String materialContent) {
        String systemPrompt = """
                你是一个文学风格分析专家。请分析用户提供的文本，提取写作风格特征。
                从以下维度分析：叙事视角、句式特征、用词偏好、节奏特点、情绪基调、代表性句式（3-5个）。
                最后整理成一段"写作风格指令"，以"请以以下风格写作："开头，200字以内。""";
        return claude.complete(systemPrompt, materialContent);
    }
}
