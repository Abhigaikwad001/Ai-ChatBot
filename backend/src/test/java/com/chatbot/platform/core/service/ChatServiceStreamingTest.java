package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.AiAuditLog;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
import com.chatbot.platform.infrastructure.exception.ConversationDeletedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceStreamingTest {

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

        conversationA = new Conversation(userA, "ICU Monitoring");
        conversationA = conversationRepository.saveAndFlush(conversationA);
    }

    @AfterEach
    void tearDown() {
        stubAiProvider.reset();
    }

    @Test
    @DisplayName("1. Successful stream persists USER first, streams chunks, and persists ASSISTANT on completion")
    void testStreamMessage_successfulStream() {
        stubAiProvider.setCustomChunks(List.of("Heart rate is ", "72 bpm and ", "stable."));

        SendMessageRequest request = new SendMessageRequest("Check telemetry readings", null);
        SseEmitter emitter = chatService.streamMessage(conversationA.getId(), userA.getId(), request);

        assertThat(emitter).isNotNull();

        // USER message was immediately persisted in Transaction 1
        List<Message> initialHistory = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(initialHistory).hasSize(1);
        assertThat(initialHistory.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(initialHistory.get(0).getContent()).isEqualTo("Check telemetry readings");
        assertThat(initialHistory.get(0).getStatus()).isEqualTo(MessageStatus.SENT);

        // Wait for async virtual thread to finish generation & Transaction 2 persistence
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
            assertThat(history).hasSize(2);
            Message assistantMsg = history.get(1);
            assertThat(assistantMsg.getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(assistantMsg.getSequenceNumber()).isEqualTo(2);
            assertThat(assistantMsg.getContent()).isEqualTo("Heart rate is 72 bpm and stable.");
            assertThat(assistantMsg.getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(assistantMsg.getMetadata()).containsEntry("provider", "OPENAI");
        });

        // Verify audit log recorded
        List<AiAuditLog> auditLogs = aiAuditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getFinishReason()).isEqualTo("stop");
    }

    @Test
    @DisplayName("2. Provider failure before any content persists USER but does NOT persist fake ASSISTANT message")
    void testStreamMessage_failureBeforeContent() {
        stubAiProvider.setFailWithUnavailable(true);

        SendMessageRequest request = new SendMessageRequest("Check ECG", null);
        SseEmitter emitter = chatService.streamMessage(conversationA.getId(), userA.getId(), request);
        assertThat(emitter).isNotNull();

        // USER message remains safely persisted
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);

        // Wait for async processing
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AiAuditLog> auditLogs = aiAuditLogRepository.findAll();
            assertThat(auditLogs).hasSize(1);
            assertThat(auditLogs.get(0).getFinishReason()).isEqualTo("ERROR");
        });

        // Invariant: exactly 1 message exists (no fake assistant message)
        List<Message> finalMessages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(finalMessages).hasSize(1);
    }

    @Test
    @DisplayName("3. Provider failure after partial content persists ASSISTANT as FAILED with metadata (not falsely SENT)")
    void testStreamMessage_failureAfterPartialContent() {
        stubAiProvider.setFailDuringStream(true);

        SendMessageRequest request = new SendMessageRequest("Generate discharge summary", null);
        SseEmitter emitter = chatService.streamMessage(conversationA.getId(), userA.getId(), request);
        assertThat(emitter).isNotNull();

        // Wait for partial stream failure to persist FAILED message
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
            assertThat(history).hasSize(2);
            Message assistantMsg = history.get(1);
            assertThat(assistantMsg.getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(assistantMsg.getStatus()).isEqualTo(MessageStatus.FAILED);
            assertThat(assistantMsg.getContent()).contains("Partial response before failure...");
            assertThat(assistantMsg.getMetadata()).containsEntry("partialStream", true);
            assertThat(assistantMsg.getMetadata()).containsEntry("finishReason", "ERROR");
        });
    }

    @Test
    @DisplayName("4. Oversized message exceeds context budget and is rejected before persistence or AI call")
    void testStreamMessage_oversizedMessageRejected() {
        // String of 25,000 characters (~6,250 tokens > 3,072 max input tokens)
        String massivePrompt = "A".repeat(25000);
        SendMessageRequest request = new SendMessageRequest(massivePrompt, null);

        assertThatThrownBy(() -> chatService.streamMessage(conversationA.getId(), userA.getId(), request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Your message is too large to process. Please shorten it and try again.");

        // No message was persisted
        List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(history).isEmpty();

        // No AI call occurred
        assertThat(stubAiProvider.getLastReceivedRequest()).isNull();
    }

    @Test
    @DisplayName("5. Access denied when user does not own conversation")
    void testStreamMessage_unauthorizedUser() {
        SendMessageRequest request = new SendMessageRequest("Unauthorized stream attempt", null);

        assertThatThrownBy(() -> chatService.streamMessage(conversationA.getId(), userB.getId(), request))
            .isInstanceOf(AccessDeniedException.class);

        List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("6. Rejected when conversation is marked DELETED")
    void testStreamMessage_deletedConversation() {
        conversationA.setStatus(ConversationStatus.DELETED);
        conversationRepository.saveAndFlush(conversationA);

        SendMessageRequest request = new SendMessageRequest("Message to deleted convo", null);

        assertThatThrownBy(() -> chatService.streamMessage(conversationA.getId(), userA.getId(), request))
            .isInstanceOf(ConversationDeletedException.class);
    }
}
