package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.api.mapper.MessageMapper;
import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.core.service.ChatService;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural verification test demonstrating that ChatService depends solely on the
 * AiProvider interface and resolves multiple distinct provider implementations dynamically.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProviderSwitchingTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiAuditLogRepository aiAuditLogRepository;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ConversationContextBuilder contextBuilder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;

    static class AlphaAiProvider implements AiProvider {
        @Override
        public ProviderType getProviderType() {
            return ProviderType.OLLAMA;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            return AiResponse.builder()
                .content("Response from ALPHA Engine (" + request.model() + ")")
                .provider("ALPHA_ENGINE")
                .model(request.model())
                .promptTokens(10)
                .completionTokens(20)
                .finishReason("stop")
                .latencyMs(12L)
                .build();
        }
    }

    static class BetaAiProvider implements AiProvider {
        @Override
        public ProviderType getProviderType() {
            return ProviderType.CUSTOM;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            return AiResponse.builder()
                .content("Response from BETA Engine (" + request.model() + ")")
                .provider("BETA_ENGINE")
                .model(request.model())
                .promptTokens(15)
                .completionTokens(25)
                .finishReason("stop")
                .latencyMs(18L)
                .build();
        }
    }

    @BeforeEach
    void setUp() {
        aiAuditLogRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("provider.switch@hospital.org", "passHash", "Dr. Switch");
        testUser.setRole(UserRole.ROLE_USER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);
    }

    @Test
    @DisplayName("Demonstrates ChatService operates seamlessly with different AiProvider implementations without code changes")
    void testChatService_switchesBetweenProvidersSeamlessly() {
        AiProperties properties = new AiProperties();
        properties.setDefaultProvider(ProviderType.OLLAMA);

        AlphaAiProvider alpha = new AlphaAiProvider();
        BetaAiProvider beta = new BetaAiProvider();
        AiProviderRegistry customRegistry = new AiProviderRegistry(List.of(alpha, beta), properties);

        ChatService dynamicChatService = new ChatService(
            conversationRepository,
            messageRepository,
            messageMapper,
            customRegistry,
            contextBuilder,
            aiAuditLogRepository,
            properties,
            transactionManager
        );

        // 1. Conversation configured with ProviderType.OLLAMA -> Alpha provider
        Conversation convoAlpha = new Conversation(testUser, "Alpha Case", "Be concise",
            new AiModelConfig(ProviderType.OLLAMA, "alpha-model", 0.5, 1024, 1.0));
        convoAlpha = conversationRepository.saveAndFlush(convoAlpha);

        MessageResponse alphaResponse = dynamicChatService.sendMessage(
            convoAlpha.getId(),
            testUser.getId(),
            new SendMessageRequest("Query to Alpha", null)
        );

        assertThat(alphaResponse.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(alphaResponse.content()).isEqualTo("Response from ALPHA Engine (alpha-model)");
        assertThat(alphaResponse.metadata()).containsEntry("provider", "ALPHA_ENGINE");

        // 2. Conversation configured with ProviderType.CUSTOM -> Beta provider
        Conversation convoBeta = new Conversation(testUser, "Beta Case", "Be thorough",
            new AiModelConfig(ProviderType.CUSTOM, "beta-model", 0.2, 2048, 0.95));
        convoBeta = conversationRepository.saveAndFlush(convoBeta);

        MessageResponse betaResponse = dynamicChatService.sendMessage(
            convoBeta.getId(),
            testUser.getId(),
            new SendMessageRequest("Query to Beta", null)
        );

        assertThat(betaResponse.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(betaResponse.content()).isEqualTo("Response from BETA Engine (beta-model)");
        assertThat(betaResponse.metadata()).containsEntry("provider", "BETA_ENGINE");
    }
}
