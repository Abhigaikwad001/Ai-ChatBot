package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiRole;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MultiTurnConversationIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiAuditLogRepository aiAuditLogRepository;

    @Autowired
    private TestStubAiProvider stubAiProvider;

    private User testUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        aiAuditLogRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("alex@hospital.org", "passHash", "Dr. Alex");
        testUser.setRole(UserRole.ROLE_USER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        conversation = new Conversation(testUser, "Multi-Turn Consultation", "You are a clinical consultation AI.", null);
        conversation = conversationRepository.saveAndFlush(conversation);
    }

    @Test
    @DisplayName("Maintains accumulated conversational history across multiple user and assistant turns")
    void testMultiTurnConversation_accumulatesHistoryDeterministically() {
        // =========================================================================
        // Turn 1: Initial question
        // =========================================================================
        stubAiProvider.setCustomResponse("Albert Einstein was a German-born theoretical physicist who developed the theory of relativity.");
        MessageResponse resp1 = chatService.sendMessage(
            conversation.getId(),
            testUser.getId(),
            new SendMessageRequest("Who is Albert Einstein?", null)
        );

        assertThat(resp1.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(resp1.sequenceNumber()).isEqualTo(2);

        AiRequest turn1Request = stubAiProvider.getLastReceivedRequest();
        assertThat(turn1Request).isNotNull();
        assertThat(turn1Request.systemPrompt()).isEqualTo("You are a clinical consultation AI.");
        assertThat(turn1Request.messages()).hasSize(1);
        assertThat(turn1Request.messages().get(0).role()).isEqualTo(AiRole.USER);
        assertThat(turn1Request.messages().get(0).content()).isEqualTo("Who is Albert Einstein?");

        // =========================================================================
        // Turn 2: Contextual follow-up ("When was he born?")
        // =========================================================================
        stubAiProvider.setCustomResponse("He was born on March 14, 1879, in Ulm, Kingdom of Württemberg, German Empire.");
        MessageResponse resp2 = chatService.sendMessage(
            conversation.getId(),
            testUser.getId(),
            new SendMessageRequest("When was he born?", null)
        );

        assertThat(resp2.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(resp2.sequenceNumber()).isEqualTo(4);

        AiRequest turn2Request = stubAiProvider.getLastReceivedRequest();
        assertThat(turn2Request).isNotNull();
        assertThat(turn2Request.messages()).hasSize(3);

        List<AiMessage> turn2Messages = turn2Request.messages();
        // Turn 1 question
        assertThat(turn2Messages.get(0).role()).isEqualTo(AiRole.USER);
        assertThat(turn2Messages.get(0).content()).isEqualTo("Who is Albert Einstein?");
        // Turn 1 answer
        assertThat(turn2Messages.get(1).role()).isEqualTo(AiRole.ASSISTANT);
        assertThat(turn2Messages.get(1).content()).contains("Albert Einstein was a German-born theoretical physicist");
        // Turn 2 question (current active user turn)
        assertThat(turn2Messages.get(2).role()).isEqualTo(AiRole.USER);
        assertThat(turn2Messages.get(2).content()).isEqualTo("When was he born?");

        // =========================================================================
        // Turn 3: Second follow-up ("What was his most famous equation?")
        // =========================================================================
        stubAiProvider.setCustomResponse("His most famous equation is E = mc², describing mass-energy equivalence.");
        MessageResponse resp3 = chatService.sendMessage(
            conversation.getId(),
            testUser.getId(),
            new SendMessageRequest("What was his most famous equation?", null)
        );

        assertThat(resp3.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(resp3.sequenceNumber()).isEqualTo(6);

        AiRequest turn3Request = stubAiProvider.getLastReceivedRequest();
        assertThat(turn3Request).isNotNull();
        assertThat(turn3Request.messages()).hasSize(5);

        List<AiMessage> turn3Messages = turn3Request.messages();
        assertThat(turn3Messages.get(0).content()).isEqualTo("Who is Albert Einstein?");
        assertThat(turn3Messages.get(1).content()).contains("Albert Einstein was a German-born");
        assertThat(turn3Messages.get(2).content()).isEqualTo("When was he born?");
        assertThat(turn3Messages.get(3).content()).contains("He was born on March 14, 1879");
        assertThat(turn3Messages.get(4).content()).isEqualTo("What was his most famous equation?");

        // Verify context metadata enriched in the assistant message
        assertThat(resp3.metadata()).containsEntry("contextMessagesIncluded", 5);
        assertThat(resp3.metadata()).containsEntry("contextTruncated", false);
        assertThat(resp3.metadata().get("contextTokensEstimated")).isNotNull();

        // Verify database state: 6 consecutive messages
        List<Message> allPersisted = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversation.getId());
        assertThat(allPersisted).hasSize(6);
        for (int i = 0; i < 6; i++) {
            assertThat(allPersisted.get(i).getSequenceNumber()).isEqualTo(i + 1);
        }
    }
}
