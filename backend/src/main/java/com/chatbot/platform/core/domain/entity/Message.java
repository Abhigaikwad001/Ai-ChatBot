package com.chatbot.platform.core.domain.entity;

import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "messages",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_messages_conversation_sequence", columnNames = {"conversation_id", "sequence_number"})
    },
    indexes = {
        @Index(name = "idx_messages_conversation_seq", columnList = "conversation_id, sequence_number ASC"),
        @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at ASC")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Message extends BaseAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MessageStatus status = MessageStatus.SENT;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageAttachment> attachments = new ArrayList<>();

    public Message(Conversation conversation, Integer sequenceNumber, MessageRole role, String content) {
        this.conversation = conversation;
        this.sequenceNumber = sequenceNumber;
        this.role = role;
        this.content = content;
        this.status = MessageStatus.SENT;
    }

    public Message(Conversation conversation, Integer sequenceNumber, MessageRole role, String content, MessageStatus status) {
        this.conversation = conversation;
        this.sequenceNumber = sequenceNumber;
        this.role = role;
        this.content = content;
        this.status = status;
    }

    public void addAttachment(MessageAttachment attachment) {
        this.attachments.add(attachment);
        attachment.setMessage(this);
    }
}
