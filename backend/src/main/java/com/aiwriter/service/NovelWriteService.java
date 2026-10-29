package com.aiwriter.service;

import com.aiwriter.ai.AiProvider;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class NovelWriteService {

    private static final Logger log = LoggerFactory.getLogger(NovelWriteService.class);

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

        return persistWhenDone(claudeService.stream(systemPrompt, userPrompt), fw, latest);
    }

    /**
     * 在当前章节后面接着写（不新开章，也不重写已有内容）。
     *
     * <p>之前只有「开始写作」（复用未完成章节，等于重写）和「继续下一章」（新开章），
     * 写到一半想让 AI 往下接没有入口。这里把已有正文作为「已写内容」传下去，
     * 提示模型从最后一句接着写，落库时把新内容**追加**到原正文后面。
     */
    public Flux<String> continueChapter(Long userId, Long frameworkId) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        if (fw.getStatus() != Framework.FrameworkStatus.ACTIVE) {
            return Flux.error(new IllegalStateException("框架已完结"));
        }
        Chapter latest = chapterService.getLatestChapter(frameworkId);
        if (latest == null) {
            return startWriting(userId, frameworkId);   // 还没有章节，等同于开始写作
        }
        latest = chapterService.markGenerating(latest.getId());

        String existing = latest.getDocument() != null && latest.getDocument().getContent() != null
                ? latest.getDocument().getContent() : "";
        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(frameworkId, existing);

        String systemPrompt = buildNovelSystemPrompt(ctx);
        String userPrompt = buildContinueChapterUserPrompt(existing);

        return persistWhenDone(claudeService.stream(systemPrompt, userPrompt), fw, latest, existing);
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

        return persistWhenDone(claudeService.stream(systemPrompt, userPrompt), fw, nextChapter);
    }

    /**
     * 把流式生成的正文落库。
     *
     * <p>之前这里只改了章节状态、并把**空的** document 内容交给冲突检测，于是：
     * 生成的内容只存在于浏览器内存里，一旦刷新就没了；冲突检测永远看不到正文。
     * 现在边写边攒，流结束时统一写入 document，再做冲突检测。
     *
     * <p>落库交给 {@link ChapterService#completeGeneration}（@Transactional）：
     * Document 是延迟加载代理，在没有 session 的 Reactor 回调里直接访问会
     * LazyInitializationException。
     */
    private Flux<String> persistWhenDone(Flux<String> source, Framework fw, Chapter chapter) {
        return persistWhenDone(source, fw, chapter, "");
    }

    /**
     * @param baseContent 章节里已有的正文；非空时表示这一轮是「接着写」，落库用追加而不是覆盖
     */
    private Flux<String> persistWhenDone(Flux<String> source, Framework fw, Chapter chapter, String baseContent) {
        StringBuilder buffer = new StringBuilder();
        return source
                .doOnNext(buffer::append)
                .doOnComplete(() -> {
                    String text = buffer.toString();
                    String full = baseContent == null || baseContent.isBlank()
                            ? text
                            : baseContent.stripTrailing() + "\n\n" + text;
                    try {
                        chapterService.completeGeneration(chapter.getId(), full);
                        if (!full.isEmpty()) {
                            conflictService.checkChapterConsistency(fw, chapter, full);
                            // 语义审校要调一次模型（阻塞），放到弹性线程池里跑，
                            // 不能在 reactor-http-nio 线程上 block()，也不能拖慢响应收尾
                            Schedulers.boundedElastic().schedule(() -> {
                                try {
                                    int found = conflictService.aiCheckAgainstElements(fw, chapter, full);
                                    if (found > 0) {
                                        log.info("AI 冲突检测: 章节 {} 发现 {} 条语义冲突", chapter.getId(), found);
                                    }
                                } catch (Exception e) {
                                    log.warn("AI 冲突检测异常 chapterId={}: {}", chapter.getId(), e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.warn("章节落库失败 chapterId={}: {}", chapter.getId(), e.getMessage(), e);
                    }
                });
    }

    public void intervene(Long userId, Long frameworkId, Long chapterId, String userContent) {
        // 归属校验：框架必须属于当前用户，避免用他人 chapterId 写入内容
        frameworkService.findOwned(userId, frameworkId);
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

    /** 「接着写本章」的提示：强调从断点往下写，不要重复、不要重开一章。 */
    private String buildContinueChapterUserPrompt(String existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本章已经写到这里】\n")
          .append(existing == null ? "" : existing.strip())
          .append("\n\n");
        sb.append("""
                请从上面最后一句直接接着往下写，要求：
                1. 不要重复、不要改写已经写过的内容，也不要重新写标题
                2. 紧接上文的情节和语气继续推进，场景不跳
                3. 再写 800-1500 字，写到这一段情节自然收住为止
                """);
        return sb.toString();
    }
}
