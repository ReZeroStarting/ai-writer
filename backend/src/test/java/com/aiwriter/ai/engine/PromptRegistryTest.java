package com.aiwriter.ai.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRegistryTest {

    private final PromptRegistry registry = new PromptRegistry();

    @Test
    @DisplayName("七个写作操作的提示词都注册了，且不为空")
    void allWritingPromptsRegistered() {
        for (String id : new String[]{"outline", "continue", "polish", "expand", "shorten", "auto-write", "imitate"}) {
            assertThat(registry.find(id)).as("prompt %s 应存在", id).isPresent();
            assertThat(registry.system(id)).isNotBlank();
            assertThat(registry.version(id)).isGreaterThanOrEqualTo(1);
        }
        assertThat(registry.all()).hasSize(7);
    }

    @Test
    @DisplayName("未注册的 id 直接抛错，避免静默用空提示词")
    void unknownIdThrows() {
        assertThatThrownBy(() -> registry.system("not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
