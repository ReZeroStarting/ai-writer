package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.ai.PromptBuilder;
import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.model.StyleTemplate;
import com.aiwriter.repository.StyleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final ClaudeService claude;
    private final PromptBuilder prompts;
    private final StyleTemplateRepository styleRepo;

    public Flux<String> generateOutline(AiRequest req) {
        log.info("AI生成大纲: topic={}", req.getContent().substring(0, Math.min(100, req.getContent().length())));
        return claude.stream(prompts.buildOutlineSystem(), "主题：" + req.getContent());
    }

    public Flux<String> continueWrite(AiRequest req) {
        log.info("AI续写: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请续写：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildContinueSystem(style), msg);
    }

    public Flux<String> polish(AiRequest req) {
        log.info("AI润色: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请润色：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildPolishSystem(style), msg);
    }

    public Flux<String> expand(AiRequest req) {
        log.info("AI扩展: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请扩展：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildExpandSystem(style), msg);
    }

    public Flux<String> shorten(AiRequest req) {
        log.info("AI精简: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请精简：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildShortenSystem(style), msg);
    }

    private String getStylePrompt(Long id) {
        if (id == null) return null;
        return styleRepo.findById(id).map(StyleTemplate::getSystemPrompt).orElse(null);
    }
}
