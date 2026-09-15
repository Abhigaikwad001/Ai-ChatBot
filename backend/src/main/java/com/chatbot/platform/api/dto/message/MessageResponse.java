package com.chatbot.platform.api.dto.message;

import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    Integer sequenceNumber,
    MessageRole role,
    String content,
    MessageStatus status,
    Integer promptTokens,
    Integer completionTokens,
    Map<String, Object> metadata,
    Instant createdAt
) {}
