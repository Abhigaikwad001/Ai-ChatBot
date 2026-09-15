package com.chatbot.platform.api.dto.stream;

import java.util.UUID;

/**
 * SSE payload emitted on the {@code message_start} event.
 * Signals to the client that generation has begun.
 */
public record MessageStartEvent(
    UUID conversationId,
    String role
) {}
