package com.chatbot.platform.api.dto.conversation;

import com.chatbot.platform.api.dto.ai.AiModelConfigDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Immutable DTO record for updating/renaming an existing conversation.
 */
public record UpdateConversationRequest(
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    String title,

    @Size(max = 10000, message = "System prompt cannot exceed 10,000 characters")
    String systemPrompt,

    @Valid
    AiModelConfigDto aiModelConfig,

    Map<String, Object> metadata
) {}
