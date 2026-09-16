package com.chatbot.platform.core.domain.entity;

import com.chatbot.platform.core.domain.enums.ConversationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "idx_conversations_user_updated", columnList = "user_id, updated_at DESC"),
    @Index(name = "idx_conversations_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Embedded
    private AiModelConfig aiModelConfig = new AiModelConfig();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<Message> messages = new ArrayList<>();

    public Conversation(String title) {
        this(null, title);
    }

    public Conversation(String title, String systemPrompt, AiModelConfig aiModelConfig) {
        this(null, title, systemPrompt, aiModelConfig);
    }

    public Conversation(User user, String title) {
        this.user = user;
        this.title = title;
        this.status = ConversationStatus.ACTIVE;
        this.aiModelConfig = AiModelConfig.defaultConfig();
    }

    public Conversation(User user, String title, String systemPrompt, AiModelConfig aiModelConfig) {
        this.user = user;
        this.title = title;
        this.systemPrompt = systemPrompt;
        this.status = ConversationStatus.ACTIVE;
        this.aiModelConfig = (aiModelConfig != null) ? aiModelConfig : AiModelConfig.defaultConfig();
    }

    public void softDelete() {
        this.status = ConversationStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    public void archive() {
        this.status = ConversationStatus.ARCHIVED;
    }

    public boolean isDeleted() {
        return this.status == ConversationStatus.DELETED;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        message.setConversation(this);
        if (message.getSequenceNumber() == null) {
            message.setSequenceNumber(this.messages.size());
        }
    }

    public void removeMessage(Message message) {
        this.messages.remove(message);
        message.setConversation(null);
    }
}
