package com.aiwriter.service;

import com.aiwriter.ai.HtmlText;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContextBuilder {
    private final FrameworkElementRepository elementRepo;
    private final ChapterRepository chapterRepo;

    public record ContextPackage(String systemContext, String recentContent, String earlierSummaries) {}

    private static final int MAX_SYSTEM_CHARS = 20000;
    private static final int MAX_RECENT_CONTENT_CHARS = 8000;
    private static final int MAX_SUMMARY_CHARS = 5000;

    public ContextPackage buildContext(Long frameworkId, String currentContent) {
        // Layer 1: All non-deprecated elements (latest version)
        var elements = elementRepo.findLatestByFrameworkId(frameworkId);
        StringBuilder systemCtx = new StringBuilder("【小说设定，写作时必须遵守】\n\n");
        for (var e : elements) {
            systemCtx.append("[").append(e.getType()).append("] ")
                     .append(e.getName()).append(": ").append(e.getContent()).append("\n\n");
        }
        if (systemCtx.length() > MAX_SYSTEM_CHARS) {
            systemCtx.setLength(MAX_SYSTEM_CHARS);
        }

        // Layer 2: Current content + previous chapter
        StringBuilder recent = new StringBuilder();
        var chapters = chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId);
        if (!chapters.isEmpty()) {
            Chapter prev = chapters.get(chapters.size() - 1);
            if (prev.getDocument() != null && prev.getDocument().getContent() != null) {
                // 文档里存的是 HTML，喂给模型前要转回纯文本
                recent.append(HtmlText.htmlToPlain(prev.getDocument().getContent()));
                if (recent.length() > MAX_RECENT_CONTENT_CHARS) {
                    recent.setLength(MAX_RECENT_CONTENT_CHARS);
                }
            }
        }
        if (currentContent != null && !currentContent.isEmpty()) {
            recent.append("\n\n").append(currentContent);
        }

        // Layer 3: Earlier chapter summaries
        StringBuilder summaries = new StringBuilder();
        int startIdx = Math.max(0, chapters.size() - 16);
        for (int i = startIdx; i < Math.min(chapters.size() - 1, startIdx + 15); i++) {
            Chapter ch = chapters.get(i);
            if (ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                summaries.append("第").append(ch.getChapterIndex()).append("章摘要: ")
                         .append(ch.getSummary()).append("\n");
            }
        }
        if (summaries.length() > MAX_SUMMARY_CHARS) {
            summaries.setLength(MAX_SUMMARY_CHARS);
        }

        return new ContextPackage(systemCtx.toString(), recent.toString(), summaries.toString());
    }
}
