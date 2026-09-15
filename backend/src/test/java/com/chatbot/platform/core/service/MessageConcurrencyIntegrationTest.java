package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.common.PageResponse;
import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MessageConcurrencyIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.chatbot.platform.infrastructure.ai.TestStubAiProvider stubAiProvider;

    private User user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("concurrent@hospital.org", "passwordHash", "Dr. Concurrency");
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        conversation = new Conversation(user, "High Concurrency Test Room");
        conversation = conversationRepository.saveAndFlush(conversation);
    }

    @Test
    @DisplayName("Concurrent message sends to the same conversation must allocate strictly unique, sequential sequence numbers")
    void testConcurrentMessageSubmission_allocatesUniqueSequenceNumbers() throws InterruptedException {
        int messageCount = 10;
        int threadPoolSize = 5;

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(messageCount);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<Integer> assignedSequences = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        UUID convId = conversation.getId();
        UUID userId = user.getId();

        for (int i = 1; i <= messageCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // Block until all threads are queued to maximize concurrency collision probability
                    startLatch.await();

                    SendMessageRequest request = new SendMessageRequest("Concurrent payload #" + index, null);
                    MessageResponse response = chatService.sendMessage(convId, userId, request);

                    assignedSequences.add(response.sequenceNumber());
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        boolean completedInTime = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(messageCount);

        // Verify with database retrieval
        PageResponse<MessageResponse> messages = chatService.getMessages(convId, userId, 0, 50);
        assertThat(messages.totalElements()).isEqualTo(messageCount * 2L);

        List<Integer> dbSequenceNumbers = messages.content().stream()
            .map(MessageResponse::sequenceNumber)
            .toList();

        // 1. Verify sequence numbers are strictly 1 through 20 with consecutive ordering
        List<Integer> expectedSequences = java.util.stream.IntStream.rangeClosed(1, messageCount * 2)
            .boxed()
            .toList();
        assertThat(dbSequenceNumbers).isEqualTo(expectedSequences);

        // 2. Verify there are zero duplicates across all turns
        Set<Integer> uniqueSet = new HashSet<>(dbSequenceNumbers);
        assertThat(uniqueSet).hasSize(messageCount * 2);
    }
}
