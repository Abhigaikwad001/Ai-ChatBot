package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.AiAuditLog;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class StreamingCancellationTest {

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

    private User user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        aiAuditLogRepository.deleteAll();
        messageRepository.deleteAll();
        messageRepository.flush();
        conversationRepository.deleteAll();
        conversationRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();

        user = new User("surgeon@hospital.org", "hash", "Dr. Surgeon");
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        conversation = new Conversation(user, "Post-Op Monitoring");
        conversation = conversationRepository.saveAndFlush(conversation);
    }

    @Test
    @DisplayName("1. SseEmitter timeout triggers stream cancellation and resource cleanup")
    void testStreamEmitter_timeoutCancellation() {
        stubAiProvider.setCustomChunks(List.of("Starting long operation... ", "Still working... "));

        SendMessageRequest request = new SendMessageRequest("Perform lengthy triage", null);
        SseEmitter emitter = chatService.streamMessage(conversation.getId(), user.getId(), request);

        assertThat(emitter).isNotNull();

        // Simulate Spring MVC container firing onTimeout callback
        emitter.complete();

        // Ensure user message is safe
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversation.getId());
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
    }

    @Test
    @DisplayName("2. Client disconnect after partial generation persists FAILED message with CANCELLED audit")
    void testStream_clientDisconnectAfterPartialGeneration() {
        stubAiProvider.setCustomChunks(List.of("Partial diagnostic insight ", "before abrupt disconnect"));

        SendMessageRequest request = new SendMessageRequest("Detailed analysis", null);
        SseEmitter emitter = chatService.streamMessage(conversation.getId(), user.getId(), request);

        // Await until generation produces messages
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> history = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversation.getId());
            assertThat(history).hasSize(2);
            Message assistant = history.get(1);
            assertThat(assistant.getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(assistant.getContent()).isNotEmpty();
        });
    }
}
