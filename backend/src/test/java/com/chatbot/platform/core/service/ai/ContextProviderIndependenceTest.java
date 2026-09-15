package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextProviderIndependenceTest {

    private ConversationContextBuilder contextBuilder;
    private User testUser;

    static class MockProvider implements AiProvider {
        private final ProviderType providerType;
        private final AtomicReference<AiRequest> capturedRequest = new AtomicReference<>();

        MockProvider(ProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public ProviderType getProviderType() {
            return providerType;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            capturedRequest.set(request);
            return AiResponse.builder()
                .content("Echo response from " + providerType)
                .provider(providerType.name())
                .model(request.model())
                .promptTokens(20)
                .completionTokens(10)
                .finishReason("stop")
                .latencyMs(15L)
                .build();
        }

        public AiRequest getCapturedRequest() {
            return capturedRequest.get();
        }
    }

    @BeforeEach
    void setUp() {
        contextBuilder = new ConversationContextBuilder();
        testUser = new User("independent@hospital.org", "passHash", "Dr. Cross");
    }

    @Test
    @DisplayName("Context Builder generates identical, provider-neutral AiRequest payload for multiple distinct providers")
    void testContextBuilder_isProviderIndependent() {
        Conversation convo = new Conversation(
            testUser,
            "Provider Independence Case",
            "General clinical assistant instructions.",
            new AiModelConfig(ProviderType.OLLAMA, "neutral-model", 0.7, 2048, 1.0)
        );

        List<Message> history = new ArrayList<>();
        history.add(new Message(convo, 1, MessageRole.USER, "What are symptoms of hypoglycemia?"));
        history.add(new Message(convo, 2, MessageRole.ASSISTANT, "Symptoms include shakiness, sweating, and confusion."));
        history.add(new Message(convo, 3, MessageRole.USER, "How should it be treated immediately?"));

        // Build context once
        AiRequest neutralRequest = contextBuilder.buildContext(convo, history);

        // Dispatch to Provider A (e.g. OLLAMA)
        MockProvider providerA = new MockProvider(ProviderType.OLLAMA);
        providerA.generate(neutralRequest);

        // Dispatch to Provider B (e.g. CUSTOM / Future Cloud Provider)
        MockProvider providerB = new MockProvider(ProviderType.CUSTOM);
        providerB.generate(neutralRequest);

        AiRequest requestA = providerA.getCapturedRequest();
        AiRequest requestB = providerB.getCapturedRequest();

        // 1. Both providers received the exact same object reference or identical content
        assertThat(requestA).isNotNull();
        assertThat(requestB).isNotNull();
        assertThat(requestA).isEqualTo(requestB);

        // 2. Structural invariants verified across both providers
        assertThat(requestA.model()).isEqualTo(requestB.model()).isEqualTo("neutral-model");
        assertThat(requestA.systemPrompt()).isEqualTo(requestB.systemPrompt()).isEqualTo("General clinical assistant instructions.");
        assertThat(requestA.messages()).hasSize(3).isEqualTo(requestB.messages());

        for (int i = 0; i < requestA.messages().size(); i++) {
            AiMessage msgA = requestA.messages().get(i);
            AiMessage msgB = requestB.messages().get(i);
            assertThat(msgA.role()).isEqualTo(msgB.role());
            assertThat(msgA.content()).isEqualTo(msgB.content());
        }
    }
}
