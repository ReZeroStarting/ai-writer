package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.ai.PromptBuilder;
import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
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

    public Flux<String> generateOutline(AiRequest req, Long userId) {
        log.info("AI生成大纲: topic={}", req.getContent().substring(0, Math.min(100, req.getContent().length())));
        return claude.stream(prompts.buildOutlineSystem(), "主题：" + req.getContent());
    }

    public Flux<String> continueWrite(AiRequest req, Long userId) {
        log.info("AI续写: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "请续写：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildContinueSystem(style), msg);
    }

    public Flux<String> polish(AiRequest req, Long userId) {
        log.info("AI润色: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "请润色：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildPolishSystem(style), msg);
    }

    public Flux<String> expand(AiRequest req, Long userId) {
        log.info("AI扩展: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "请扩展：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildExpandSystem(style), msg);
    }

    public Flux<String> shorten(AiRequest req, Long userId) {
        log.info("AI精简: styleTemplateId={}, contentLen={}", req.getStyleTemplateId(), req.getContent().length());
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "请精简：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildShortenSystem(style), msg);
    }

    public Flux<String> autoWrite(AiRequest req, Long userId) {
        log.info("AI自动写作: topic={}", req.getContent().substring(0, Math.min(100, req.getContent().length())));
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "写作主题：" + req.getContent();
        if (req.getInstruction() != null) msg += "\n\n补充要求：" + req.getInstruction();
        return claude.stream(prompts.buildAutoWriteSystem(style), msg);
    }

    public Flux<String> imitateWrite(AiRequest req, Long userId) {
        log.info("AI仿写: refLen={}, topic={}", req.getContent().length(),
                req.getInstruction() != null ? req.getInstruction().substring(0, Math.min(50, req.getInstruction().length())) : "none");
        String style = getStylePrompt(req.getStyleTemplateId(), userId);
        String msg = "参考文本（请模仿其风格）：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg += "\n\n写作主题：" + req.getInstruction();
        return claude.stream(prompts.buildImitateSystem(style), msg);
    }

    /**
     * 解析风格模板的 systemPrompt。
     *
     * 三种情况必须区分对待，不能一律静默返回 null：
     * - 未传 styleTemplateId：正常场景（用户没选风格），返回 null 即无风格生成；
     * - 传了 id 但记录不存在：404，否则用户会以为"带了风格"，实际风格被悄悄丢掉；
     * - 记录存在但不属于当前用户：403，避免把他人的 systemPrompt 读进生成上下文。
     */
    private String getStylePrompt(Long id, Long userId) {
        if (id == null) return null;
        StyleTemplate template = styleRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("风格模板不存在: " + id));
        if (!template.getUser().getId().equals(userId)) {
            throw new ForbiddenException("无权使用此风格模板");
        }
        return template.getSystemPrompt();
    }
}
