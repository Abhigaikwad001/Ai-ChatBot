package com.chatbot.platform.api.dto.conversation;

import com.chatbot.platform.api.dto.ai.AiModelConfigDto;
import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.core.domain.enums.ConversationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID userId,
    String title,
    String systemPrompt,
    AiModelConfigDto aiModelConfig,
    ConversationStatus status,
    Map<String, Object> metadata,
    List<MessageResponse> messages,
    Instant createdAt,
    Instant updatedAt
) {}
