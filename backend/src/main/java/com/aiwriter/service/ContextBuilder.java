package com.aiwriter.service;

import com.aiwriter.ai.HtmlText;
import com.aiwriter.ai.rag.ElementRanker;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组装续写用的四层上下文。
 *
 * <p>四层的顺序与预算（都是刻意定的，改动前先想清楚为什么）：
 * <ol>
 *   <li><b>设定</b>：全部非弃用元素的最新版，预算 {@value #MAX_SYSTEM_CHARS} 字。
 *       超预算时按与当前正文的相关性取 top-K，而不是按顺序截断——</li>
 *   <li><b>前章正文 + 当前内容</b>：预算 {@value #MAX_RECENT_CONTENT_CHARS} 字；</li>
 *   <li><b>近若干章摘要</b>：预算 {@value #MAX_SUMMARY_CHARS} 字；</li>
 *   <li>以上全部塞进 system + user 两个位置。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);

    private final FrameworkElementRepository elementRepo;
    private final ChapterRepository chapterRepo;
    private final ElementRanker ranker;

    public record ContextPackage(String systemContext, String recentContent, String earlierSummaries) {}

    private static final int MAX_SYSTEM_CHARS = 20000;
    private static final int MAX_RECENT_CONTENT_CHARS = 8000;
    private static final int MAX_SUMMARY_CHARS = 5000;

    public ContextPackage buildContext(Long frameworkId, String currentContent) {
        // Layer 1: 全部非弃用元素（最新版本），超预算时按相关性筛选
        List<FrameworkElement> elements = elementRepo.findLatestByFrameworkId(frameworkId);
        String systemCtx = renderElements(elements);
        if (systemCtx.length() > MAX_SYSTEM_CHARS && !elements.isEmpty()) {
            List<FrameworkElement> selected = ranker.rank(elements, currentContent);
            systemCtx = renderElements(selected);
            log.info("设定超出预算（{} 字），按相关性保留 {} / {} 条",
                    renderElements(elements).length(), selected.size(), elements.size());
        }
        if (systemCtx.length() > MAX_SYSTEM_CHARS) {
            systemCtx = systemCtx.substring(0, MAX_SYSTEM_CHARS);
        }

        // Layer 2: 前一章正文 + 当前内容
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

        // Layer 3: 更早章节的摘要
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

        return new ContextPackage(systemCtx, recent.toString(), summaries.toString());
    }

    private String renderElements(List<FrameworkElement> elements) {
        StringBuilder sb = new StringBuilder("【小说设定，写作时必须遵守】\n\n");
        for (var e : elements) {
            sb.append("[").append(e.getType()).append("] ")
              .append(e.getName()).append(": ").append(e.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
