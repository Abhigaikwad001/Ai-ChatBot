package com.chatbot.platform.api.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Immutable DTO record for sending a message to a conversation.
 */
public record SendMessageRequest(
    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 10000, message = "Message content cannot exceed 10,000 characters")
    String content,

    Map<String, Object> metadata
) {}
