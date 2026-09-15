package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderRegistryTest {

    private AiProperties properties;
    private StubProvider ollamaProvider;
    private StubProvider customProvider;
    private AiProviderRegistry registry;

    static class StubProvider implements AiProvider {
        private final ProviderType type;

        StubProvider(ProviderType type) {
            this.type = type;
        }

        @Override
        public ProviderType getProviderType() {
            return type;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            return AiResponse.builder()
                .content("Stub response from " + type)
                .provider(type.name())
                .model(request.model())
                .build();
        }
    }

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setDefaultProvider(ProviderType.OLLAMA);

        ollamaProvider = new StubProvider(ProviderType.OLLAMA);
        customProvider = new StubProvider(ProviderType.CUSTOM);

        registry = new AiProviderRegistry(List.of(ollamaProvider, customProvider), properties);
    }

    @Test
    @DisplayName("1. Resolves registered provider by explicit ProviderType")
    void testGetProvider_byExplicitType() {
        AiProvider provider = registry.getProvider(ProviderType.OLLAMA);
        assertThat(provider).isSameAs(ollamaProvider);

        AiProvider custom = registry.getProvider(ProviderType.CUSTOM);
        assertThat(custom).isSameAs(customProvider);
    }

    @Test
    @DisplayName("2. Resolves default provider when null type is supplied")
    void testGetProvider_nullResolvesDefault() {
        AiProvider defaultProvider = registry.getProvider(null);
        assertThat(defaultProvider).isSameAs(ollamaProvider);
    }

    @Test
    @DisplayName("3. Throws AiProviderUnavailableException when requested provider is not registered")
    void testGetProvider_unregisteredThrowsException() {
        assertThatThrownBy(() -> registry.getProvider(ProviderType.OPENAI))
            .isInstanceOf(AiProviderUnavailableException.class)
            .hasMessageContaining("AI provider is not configured or supported on this system: OPENAI");
    }

    @Test
    @DisplayName("4. Checks provider presence via hasProvider and inspects registered types")
    void testHasProviderAndGetRegisteredTypes() {
        assertThat(registry.hasProvider(ProviderType.OLLAMA)).isTrue();
        assertThat(registry.hasProvider(ProviderType.CUSTOM)).isTrue();
        assertThat(registry.hasProvider(ProviderType.ANTHROPIC)).isFalse();
        assertThat(registry.getRegisteredTypes()).containsExactlyInAnyOrder(ProviderType.OLLAMA, ProviderType.CUSTOM);
    }
}
