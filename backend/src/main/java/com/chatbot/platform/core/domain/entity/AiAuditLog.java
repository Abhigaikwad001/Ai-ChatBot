package com.chatbot.platform.core.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "ai_audit_logs", indexes = {
    @Index(name = "idx_audit_logs_conv_created", columnList = "conversation_id, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class AiAuditLog extends BaseAuditableEntity {

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "finish_reason", length = 50)
    private String finishReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public AiAuditLog(UUID conversationId, UUID messageId, String provider, String model, Long latencyMs, Integer totalTokens) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.provider = provider;
        this.model = model;
        this.latencyMs = latencyMs;
        this.totalTokens = totalTokens;
    }
}
