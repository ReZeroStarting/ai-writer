package com.aiwriter.service;

import com.aiwriter.ai.AiProvider;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class NovelWriteService {
    private final AiProvider claudeService;
    private final ContextBuilder contextBuilder;
    private final ChapterService chapterService;
    private final ConflictService conflictService;
    private final FrameworkService frameworkService;
    private final DocumentRepository documentRepo;
    private final ChapterRepository chapterRepo;

    public Flux<String> startWriting(Long userId, Long frameworkId) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        if (fw.getStatus() != Framework.FrameworkStatus.ACTIVE) {
            return Flux.error(new IllegalStateException("框架已完结"));
        }

        // Get or create current chapter
        Chapter latest = chapterService.getLatestChapter(frameworkId);
        if (latest == null || latest.getStatus() == Chapter.ChapterStatus.COMPLETED) {
            int nextIdx = (latest != null) ? latest.getChapterIndex() + 1 : 1;
            Document doc = createEmptyDocument(userId, fw.getProject().getId());
            latest = chapterService.createChapter(fw, nextIdx, doc.getId());
        }
        latest = chapterService.markGenerating(latest.getId());

        String currentContent = latest.getDocument() != null && latest.getDocument().getContent() != null
                ? latest.getDocument().getContent() : "";
        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(frameworkId, currentContent);

        String systemPrompt = buildNovelSystemPrompt(ctx);
        String userPrompt = buildContinueUserPrompt(ctx, currentContent);

        final Chapter chapterRef = latest;
        return claudeService.stream(systemPrompt, userPrompt)
                .doOnComplete(() -> {
                    chapterService.markGenerated(chapterRef.getId(), null);
                    Document doc = chapterRef.getDocument();
                    if (doc != null) {
                        conflictService.checkChapterConsistency(fw, chapterRef, doc.getContent());
                    }
                });
    }

    public Flux<String> continueWriting(Long userId, Long frameworkId) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        if (fw.getStatus() != Framework.FrameworkStatus.ACTIVE) {
            return Flux.error(new IllegalStateException("框架已完结"));
        }

        Chapter latest = chapterService.getLatestChapter(frameworkId);
        int nextIdx = (latest != null) ? latest.getChapterIndex() + 1 : 1;
        Document doc = createEmptyDocument(userId, fw.getProject().getId());
        Chapter nextChapter = chapterService.createChapter(fw, nextIdx, doc.getId());
        nextChapter = chapterService.markGenerating(nextChapter.getId());

        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(frameworkId, "");

        String systemPrompt = buildNovelSystemPrompt(ctx);
        String userPrompt = buildContinueUserPrompt(ctx, "");

        final Chapter chapterRef = nextChapter;
        return claudeService.stream(systemPrompt, userPrompt)
                .doOnComplete(() -> {
                    chapterService.markGenerated(chapterRef.getId(), null);
                    Document doc2 = chapterRef.getDocument();
                    if (doc2 != null) {
                        conflictService.checkChapterConsistency(fw, chapterRef, doc2.getContent());
                    }
                });
    }

    public void intervene(Long userId, Long frameworkId, Long chapterId, String userContent) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        Chapter ch = chapterRepo.findById(chapterId).orElseThrow();
        Document doc = ch.getDocument();
        if (doc != null) {
            String current = doc.getContent() != null ? doc.getContent() : "";
            doc.setContent(current + "\n\n【用户插入】\n" + userContent + "\n");
            documentRepo.save(doc);
        }
        ch.setStatus(Chapter.ChapterStatus.USER_EDITING);
        chapterRepo.save(ch);
    }

    public void saveChapterContent(Long userId, Long frameworkId, Long chapterId, String content) {
        // 双重归属校验：框架归属 + 章节确实属于该框架，杜绝跨用户/跨框架越权写入
        frameworkService.findOwned(userId, frameworkId);
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        if (ch.getFramework() == null || !frameworkId.equals(ch.getFramework().getId())) {
            throw new ForbiddenException("该章节不属于指定框架");
        }
        Document doc = ch.getDocument();
        if (doc != null) {
            doc.setContent(content);
            documentRepo.save(doc);
        }
    }

    private Document createEmptyDocument(Long userId, Long projectId) {
        Document doc = Document.builder()
                .project(Project.builder().id(projectId).build())
                .user(User.builder().id(userId).build())
                .title("第?章")
                .content("")
                .build();
        return documentRepo.save(doc);
    }

    private String buildNovelSystemPrompt(ContextBuilder.ContextPackage ctx) {
        return """
            你是一位专业的小说家。根据提供的设定资料续写小说。

            """ + ctx.systemContext() + """

            【写作要求】
            1. 严格遵守以上设定，不与之产生矛盾
            2. 保持情节连贯，与前文衔接自然
            3. 语言流畅，描写生动
            4. 每章字数 1000-3000 字
            5. 在本章自然收尾，为下一章留悬念
            """;
    }

    private String buildContinueUserPrompt(ContextBuilder.ContextPackage ctx, String currentContent) {
        StringBuilder sb = new StringBuilder();
        if (!ctx.earlierSummaries().isEmpty()) {
            sb.append("【前情提要】\n").append(ctx.earlierSummaries()).append("\n\n");
        }
        if (!ctx.recentContent().isEmpty()) {
            sb.append("【前文内容】\n").append(ctx.recentContent()).append("\n\n");
        }
        sb.append("请续写下一章内容。");
        return sb.toString();
    }
}
