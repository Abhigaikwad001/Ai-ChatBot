package com.chatbot.platform.api.dto.conversation;

import com.chatbot.platform.api.dto.ai.AiModelConfigDto;
import com.chatbot.platform.core.domain.enums.ConversationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationSummaryResponse(
    UUID id,
    UUID userId,
    String title,
    AiModelConfigDto aiModelConfig,
    ConversationStatus status,
    Map<String, Object> metadata,
    int messageCount,
    Instant createdAt,
    Instant updatedAt
) {}
