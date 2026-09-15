package com.chatbot.platform.api.dto.stream;

import java.util.UUID;

/**
 * Final SSE payload emitted on the {@code message_complete} event.
 * Provides conclusive metadata allowing clients to finalize the UI turn and record usage.
 */
public record MessageCompleteEvent(
    UUID messageId,
    UUID conversationId,
    String role,
    String provider,
    String model,
    String finishReason,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long latencyMs,
    Long timeToFirstChunkMs
) {}
