package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.AiAuditLog;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
import com.chatbot.platform.infrastructure.exception.ConversationDeletedException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceAiIntegrationTest {

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

    private User userA;
    private User userB;
    private Conversation conversationA;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        aiAuditLogRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = new User("alice@hospital.org", "passHash", "Dr. Alice");
        userA.setRole(UserRole.ROLE_USER);
        userA.setStatus(UserStatus.ACTIVE);
        userA = userRepository.saveAndFlush(userA);

        userB = new User("bob@hospital.org", "passHash", "Dr. Bob");
        userB.setRole(UserRole.ROLE_USER);
        userB.setStatus(UserStatus.ACTIVE);
        userB = userRepository.saveAndFlush(userB);

        conversationA = new Conversation(userA, "Oncology Case Study");
        conversationA = conversationRepository.saveAndFlush(conversationA);
    }

    @Test
    @DisplayName("1. Successful AI generation persists USER and ASSISTANT messages, updates recency, and records audit log")
    void testSendMessage_successfulAiGeneration() {
        Instant beforeSend = conversationA.getUpdatedAt();

        SendMessageRequest request = new SendMessageRequest("Patient has elevated CA-125 markers.", null);
        MessageResponse response = chatService.sendMessage(conversationA.getId(), userA.getId(), request);

        // 1. Returned response is the newly generated ASSISTANT message
        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(response.sequenceNumber()).isEqualTo(2);
        assertThat(response.content())
                .contains("AI clinical assistant analysis for: Patient has elevated CA-125 markers.");
        assertThat(response.promptTokens()).isEqualTo(14);
        assertThat(response.completionTokens()).isEqualTo(32);
        assertThat(response.metadata()).containsEntry("provider", "OPENAI");

        // 2. Both USER (seq 1) and ASSISTANT (seq 2) messages are saved in database
        List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(history).hasSize(2);

        Message userMsg = history.get(0);
        assertThat(userMsg.getSequenceNumber()).isEqualTo(1);
        assertThat(userMsg.getRole()).isEqualTo(MessageRole.USER);
        assertThat(userMsg.getContent()).isEqualTo("Patient has elevated CA-125 markers.");

        Message assistantMsg = history.get(1);
        assertThat(assistantMsg.getSequenceNumber()).isEqualTo(2);
        assertThat(assistantMsg.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistantMsg.getContent()).contains("AI clinical assistant analysis for:");

        // 3. Conversation updatedAt is updated
        Conversation updatedConvo = conversationRepository.findById(conversationA.getId()).orElseThrow();
        assertThat(updatedConvo.getUpdatedAt()).isAfterOrEqualTo(beforeSend);

        // 4. Audit log entry is recorded
        List<AiAuditLog> auditLogs = aiAuditLogRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationA.getId());
        assertThat(auditLogs).hasSize(1);
        AiAuditLog log = auditLogs.get(0);
        assertThat(log.getProvider()).isEqualTo("OPENAI");
        assertThat(log.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(log.getMessageId()).isEqualTo(assistantMsg.getId());
        assertThat(log.getTotalTokens()).isEqualTo(46);
        assertThat(log.getFinishReason()).isEqualTo("stop");
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("2. AI generation failure preserves USER message, does not create fake assistant message, and logs audit failure")
    void testSendMessage_providerFailure_preservesUserMessageAndLogsError() {
        stubAiProvider.setFailWithTimeout(true);

        SendMessageRequest request = new SendMessageRequest("Stat ECG analysis requested.", null);

        assertThatThrownBy(() -> chatService.sendMessage(conversationA.getId(), userA.getId(), request))
                .isInstanceOf(AiProviderTimeoutException.class)
                .hasMessageContaining("Simulated test timeout");

        // 1. USER message remains safely saved in the database
        List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(history.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(history.get(0).getContent()).isEqualTo("Stat ECG analysis requested.");

        // 2. No fake or error assistant message exists
        assertThat(history).noneMatch(m -> m.getRole() == MessageRole.ASSISTANT);

        // 3. Audit log contains failure record
        List<AiAuditLog> auditLogs = aiAuditLogRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationA.getId());
        assertThat(auditLogs).hasSize(1);
        AiAuditLog log = auditLogs.get(0);
        assertThat(log.getProvider()).isEqualTo("OPENAI");
        assertThat(log.getMessageId()).isNull();
        assertThat(log.getErrorMessage()).contains("Simulated test timeout");
    }

    @Test
    @DisplayName("3. Cross-user message submission is blocked with AccessDeniedException")
    void testSendMessage_crossUserBoundary_throwsAccessDenied() {
        SendMessageRequest request = new SendMessageRequest("Unauthorized access attempt", null);

        assertThatThrownBy(() -> chatService.sendMessage(conversationA.getId(), userB.getId(), request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");

        assertThat(messageRepository.countByConversationId(conversationA.getId())).isZero();
    }

    @Test
    @DisplayName("4. Sending message to deleted conversation throws ConversationDeletedException")
    void testSendMessage_deletedConversation_throwsConversationDeletedException() {
        conversationA.setStatus(ConversationStatus.DELETED);
        conversationRepository.saveAndFlush(conversationA);

        SendMessageRequest request = new SendMessageRequest("Post to deleted", null);

        assertThatThrownBy(() -> chatService.sendMessage(conversationA.getId(), userA.getId(), request))
                .isInstanceOf(ConversationDeletedException.class)
                .hasMessageContaining("Cannot send messages to a deleted conversation");
    }
}
