package com.chatbot.platform.api.dto.stream;

/**
 * SSE payload emitted on the {@code error} event.
 * Provides sanitized error codes and messages without exposing stack traces or internal credentials.
 */
public record StreamErrorEvent(
    String code,
    String message
) {}
