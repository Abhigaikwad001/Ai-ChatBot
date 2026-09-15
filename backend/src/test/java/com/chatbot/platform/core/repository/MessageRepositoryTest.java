package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(new User("patient.bob@hospital.org", "pwdHash", "Patient Bob"));
        testConversation = conversationRepository.saveAndFlush(new Conversation(user, "Post-op Follow-up"));
    }

    @Test
    @DisplayName("Should persist ordered sequence of messages and maintain deterministic order")
    void shouldPersistMessagesInDeterministicSequenceOrder() {
        int seq1 = messageRepository.getNextSequenceNumber(testConversation.getId());
        Message m1 = new Message(testConversation, seq1, MessageRole.SYSTEM,
                "You are an empathetic medical assistant.");
        messageRepository.save(m1);

        int seq2 = messageRepository.getNextSequenceNumber(testConversation.getId());
        Message m2 = new Message(testConversation, seq2, MessageRole.USER, "When can I remove my surgical dressing?");
        messageRepository.save(m2);

        int seq3 = messageRepository.getNextSequenceNumber(testConversation.getId());
        Message m3 = new Message(testConversation, seq3, MessageRole.ASSISTANT,
                "Usually after 48 hours unless instructed otherwise by your surgeon.");
        m3.setPromptTokens(24);
        m3.setCompletionTokens(18);
        m3.setMetadata(Map.of("model", "llama3.2:3b", "latencyMs", 340));
        messageRepository.save(m3);

        messageRepository.flush();

        List<Message> orderedHistory = messageRepository
                .findByConversationIdOrderBySequenceNumberAsc(testConversation.getId());

        assertThat(orderedHistory).hasSize(3);
        assertThat(orderedHistory.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(orderedHistory.get(0).getRole()).isEqualTo(MessageRole.SYSTEM);

        assertThat(orderedHistory.get(1).getSequenceNumber()).isEqualTo(2);
        assertThat(orderedHistory.get(1).getRole()).isEqualTo(MessageRole.USER);

        assertThat(orderedHistory.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(orderedHistory.get(2).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(orderedHistory.get(2).getPromptTokens()).isEqualTo(24);
        assertThat(orderedHistory.get(2).getMetadata()).containsEntry("model", "llama3.2:3b");

        // Verify next sequence number generator
        int nextSeq = messageRepository.getNextSequenceNumber(testConversation.getId());
        assertThat(nextSeq).isEqualTo(4);
    }

    @Test
    @DisplayName("Should paginate messages in ascending sequence order")
    void shouldPaginateMessages() {
        for (int i = 1; i <= 5; i++) {
            Message m = new Message(
                    testConversation,
                    i,
                    (i % 2 == 1) ? MessageRole.USER : MessageRole.ASSISTANT,
                    "Message payload " + i,
                    MessageStatus.SENT);
            messageRepository.save(m);
        }
        messageRepository.flush();

        Page<Message> page1 = messageRepository.findByConversationIdOrderBySequenceNumberAsc(
                testConversation.getId(),
                PageRequest.of(0, 3));

        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getContent()).hasSize(3);
        assertThat(page1.getContent().get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(page1.getContent().get(2).getSequenceNumber()).isEqualTo(3);

        Page<Message> page2 = messageRepository.findByConversationIdOrderBySequenceNumberAsc(
                testConversation.getId(),
                PageRequest.of(1, 3));
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page2.getContent().get(0).getSequenceNumber()).isEqualTo(4);
        assertThat(page2.getContent().get(1).getSequenceNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should cascade delete messages when conversation is deleted")
    void shouldCascadeDeleteMessages() {
        Message m1 = new Message(testConversation, 1, MessageRole.USER, "Hello");
        testConversation.addMessage(m1);
        conversationRepository.saveAndFlush(testConversation);

        assertThat(messageRepository.countByConversationId(testConversation.getId())).isEqualTo(1);

        conversationRepository.delete(testConversation);
        conversationRepository.flush();

        assertThat(messageRepository.countByConversationId(testConversation.getId())).isZero();
    }
}
