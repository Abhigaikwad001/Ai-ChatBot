package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ConversationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.saveAndFlush(new User("patient@hospital.org", "secretHash", "Patient Alice"));
    }

    @Test
    @DisplayName("Should persist conversation with embedded AI config and JSON metadata")
    void shouldPersistConversationWithAiConfigAndMetadata() {
        AiModelConfig config = new AiModelConfig(ProviderType.OLLAMA, "mistral", 0.5, 4096, 0.95);
        Conversation conversation = new Conversation(testUser, "General Symptoms Inquiry", "You are a helpful triage assistant.", config);
        conversation.setMetadata(Map.of("department", "Cardiology", "priority", "MEDIUM"));

        Conversation saved = conversationRepository.saveAndFlush(conversation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getAiModelConfig().getProvider()).isEqualTo(ProviderType.OLLAMA);
        assertThat(saved.getAiModelConfig().getModelName()).isEqualTo("mistral");
        assertThat(saved.getMetadata()).containsEntry("department", "Cardiology");
        assertThat(saved.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should soft delete conversation and filter out deleted records in paginated query")
    void shouldSoftDeleteAndExcludeFromActiveQueries() {
        Conversation c1 = conversationRepository.save(new Conversation(testUser, "Active Chat 1"));
        Conversation c2 = conversationRepository.save(new Conversation(testUser, "Chat To Be Deleted"));
        conversationRepository.flush();

        // Perform soft delete
        c2.softDelete();
        conversationRepository.saveAndFlush(c2);

        assertThat(c2.isDeleted()).isTrue();
        assertThat(c2.getDeletedAt()).isNotNull();

        // Paginated query excluding DELETED
        Page<Conversation> activePage = conversationRepository.findByUserIdAndStatusNot(
            testUser.getId(),
            ConversationStatus.DELETED,
            PageRequest.of(0, 10)
        );

        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(activePage.getContent().get(0).getTitle()).isEqualTo("Active Chat 1");

        // Specific lookup excluding DELETED
        Optional<Conversation> deletedLookup = conversationRepository.findByIdAndUserIdAndStatusNot(
            c2.getId(),
            testUser.getId(),
            ConversationStatus.DELETED
        );
        assertThat(deletedLookup).isEmpty();

        // Direct ID lookup still preserves historical audit record
        Optional<Conversation> directLookup = conversationRepository.findById(c2.getId());
        assertThat(directLookup).isPresent();
        assertThat(directLookup.get().getStatus()).isEqualTo(ConversationStatus.DELETED);
    }
}
