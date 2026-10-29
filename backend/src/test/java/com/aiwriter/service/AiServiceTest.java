package com.aiwriter.service;

import com.aiwriter.ai.AiProvider;
import com.aiwriter.ai.AiProviderResolver;
import com.aiwriter.ai.PromptBuilder;
import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.StyleTemplate;
import com.aiwriter.model.User;
import com.aiwriter.repository.StyleTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AiService 风格模板解析语义测试。
 *
 * 重点：getStylePrompt 过去把「没传」「不存在」「不属于你」三种情况一律当 null 静默处理，
 * 导致用户选了风格却被悄悄忽略，且越权读取无从察觉。这里锁定三态必须可区分。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    // 依赖接口而非具体 provider，测试与具体实现（Anthropic/DeepSeek）解耦
    @Mock private AiProvider claude;
    @Mock private AiProviderResolver claudeResolver;
    @Mock private StyleTemplateRepository styleRepo;

    private AiService aiService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @BeforeEach
    void setUp() {
        // PromptBuilder 无外部依赖，用真实例以便验证 style 真的拼进了 system prompt
        aiService = new AiService(claudeResolver, new PromptBuilder(), styleRepo);
    when(claudeResolver.forUser(any())).thenReturn(claude);
        when(claude.stream(anyString(), anyString())).thenReturn(Flux.just("chunk"));
    }

    private AiRequest reqWithStyle(Long styleId) {
        AiRequest req = new AiRequest();
        req.setContent("测试内容");
        req.setStyleTemplateId(styleId);
        return req;
    }

    private StyleTemplate styleOwnedBy(Long ownerId, String prompt) {
        User u = User.builder().id(ownerId).build();
        return StyleTemplate.builder().id(10L).user(u).name("风格").systemPrompt(prompt).build();
    }

    /** 未传 styleTemplateId：正常场景，无风格生成，不应抛异常。 */
    @Test
    void shouldAllowMissingStyleTemplateId() {
        Flux<String> flux = aiService.continueWrite(reqWithStyle(null), OWNER_ID);

        assertNotNull(flux);
        verify(styleRepo, never()).findById(any());
        verify(claude).stream(anyString(), contains("测试内容"));
    }

    /** 传了 id 但记录不存在：必须 404，而不是静默丢弃风格。 */
    @Test
    void shouldThrowNotFoundWhenStyleTemplateMissing() {
        when(styleRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> aiService.continueWrite(reqWithStyle(99L), OWNER_ID));
        verify(claude, never()).stream(anyString(), anyString());
    }

    /** 模板存在但不属于当前用户：必须 403，且不得把他人 prompt 传进上游。 */
    @Test
    void shouldThrowForbiddenWhenStyleTemplateOfAnotherUser() {
        when(styleRepo.findById(10L))
                .thenReturn(Optional.of(styleOwnedBy(OTHER_ID, "他人的私密提示词")));

        assertThrows(ForbiddenException.class,
                () -> aiService.polish(reqWithStyle(10L), OWNER_ID));
        // 关键：越权时绝不能调用上游，否则他人 prompt 会进入生成上下文
        verify(claude, never()).stream(anyString(), anyString());
    }

    /** 正常持有的风格：system prompt 中应出现该风格要求。 */
    @Test
    void shouldInjectOwnedStyleIntoSystemPrompt() {
        when(styleRepo.findById(10L))
                .thenReturn(Optional.of(styleOwnedBy(OWNER_ID, "简洁犀利，多用短句")));

        aiService.continueWrite(reqWithStyle(10L), OWNER_ID);

        verify(claude).stream(argThat(s -> s.contains("简洁犀利，多用短句")), anyString());
    }

    /** 六个带风格的入口行为必须一致，逐个覆盖避免漏改。 */
    @Test
    void allStyledEndpointsShouldRejectForbiddenStyle() {
        when(styleRepo.findById(10L))
                .thenReturn(Optional.of(styleOwnedBy(OTHER_ID, "x")));

        assertThrows(ForbiddenException.class, () -> aiService.continueWrite(reqWithStyle(10L), OWNER_ID));
        assertThrows(ForbiddenException.class, () -> aiService.polish(reqWithStyle(10L), OWNER_ID));
        assertThrows(ForbiddenException.class, () -> aiService.expand(reqWithStyle(10L), OWNER_ID));
        assertThrows(ForbiddenException.class, () -> aiService.shorten(reqWithStyle(10L), OWNER_ID));
        assertThrows(ForbiddenException.class, () -> aiService.autoWrite(reqWithStyle(10L), OWNER_ID));
        assertThrows(ForbiddenException.class, () -> aiService.imitateWrite(reqWithStyle(10L), OWNER_ID));
    }

    /** 不带风格的入口（生成大纲）不应触碰风格仓库。 */
    @Test
    void outlineShouldNotTouchStyleRepo() {
        AiRequest req = new AiRequest();
        req.setContent("主题");

        aiService.generateOutline(req, OWNER_ID);

        verify(styleRepo, never()).findById(any());
    }
}
