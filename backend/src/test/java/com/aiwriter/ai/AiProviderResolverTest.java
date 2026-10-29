package com.aiwriter.ai;

import com.aiwriter.model.AiSetting;
import com.aiwriter.repository.AiSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 「这次调用用谁的 key」的解析规则。
 *
 * <p>核心只有一条：用户配了就用他的，没配/坏了就回退服务端默认——
 * 绝不能因为这个功能把写作整条链路弄挂。
 */
@ExtendWith(MockitoExtension.class)
class AiProviderResolverTest {

    private static final String SECRET = "unit-test-secret";

    @Mock private AiProvider serverDefault;
    @Mock private AiSettingRepository settings;

    private AiProviderResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AiProviderResolver(serverDefault, settings, SECRET);
    }

    @Test
    void shouldFallBackToServerDefaultWhenNothingSaved() {
        when(settings.findById(1L)).thenReturn(Optional.empty());
        assertSame(serverDefault, resolver.forUser(1L));
    }

    @Test
    void shouldUseUserKeyWhenSaved() {
        AiSetting s = AiSetting.builder()
                .userId(1L).provider("DEEPSEEK")
                .apiKeyEnc(SecretBox.encrypt("sk-user-own-key-123456", SECRET))
                .model("deepseek-chat").build();
        when(settings.findById(1L)).thenReturn(Optional.of(s));

        AiProvider provider = resolver.forUser(1L);

        assertNotSame(serverDefault, provider);
        assertEquals("deepseek", provider.providerName());
    }

    @Test
    void shouldBuildAnthropicWhenUserPickedIt() {
        AiSetting s = AiSetting.builder()
                .userId(2L).provider("ANTHROPIC")
                .apiKeyEnc(SecretBox.encrypt("sk-ant-1234567890", SECRET)).build();
        when(settings.findById(2L)).thenReturn(Optional.of(s));

        assertEquals("anthropic", resolver.forUser(2L).providerName());
    }

    @Test
    void shouldFallBackWhenCiphertextCannotBeRead() {
        // 比如服务器换了 key-secret，旧密文解不开：不能 500，回退默认
        AiSetting broken = AiSetting.builder()
                .userId(3L).provider("DEEPSEEK").apiKeyEnc("v1:not-a-valid-payload").build();
        when(settings.findById(3L)).thenReturn(Optional.of(broken));

        assertSame(serverDefault, resolver.forUser(3L));
    }

    @Test
    void shouldFallBackWhenKeyBlank() {
        AiSetting blank = AiSetting.builder().userId(4L).provider("DEEPSEEK").apiKeyEnc("").build();
        when(settings.findById(4L)).thenReturn(Optional.of(blank));
        assertSame(serverDefault, resolver.forUser(4L));
    }
}
