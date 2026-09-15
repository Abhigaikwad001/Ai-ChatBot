package com.chatbot.platform.api.dto.message;

import com.chatbot.platform.core.domain.enums.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateMessageRequest(
    @NotNull(message = "Message role is required")
    MessageRole role,

    @NotBlank(message = "Content cannot be blank")
    @Size(max = 50000, message = "Message content cannot exceed 50,000 characters")
    String content,

    Map<String, Object> metadata
) {}
