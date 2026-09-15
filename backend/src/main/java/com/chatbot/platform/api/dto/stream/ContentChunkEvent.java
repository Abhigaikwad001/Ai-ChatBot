package com.chatbot.platform.api.dto.stream;

/**
 * SSE payload emitted on each incremental {@code content} event.
 */
public record ContentChunkEvent(
    String content,
    int sequence
) {}
