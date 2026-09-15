package com.chatbot.platform.core.service.ai.stream;

import java.util.Objects;

/**
 * Provider-neutral representation of an incremental chunk of streamed AI response content.
 *
 * @param content the incremental textual content delta
 * @param model the model producing this chunk
 * @param chunkIndex zero-based sequence index of the chunk
 * @param isLast whether this chunk is marked as the final chunk by the upstream provider
 */
public record AiStreamChunk(
    String content,
    String model,
    int chunkIndex,
    boolean isLast
) {
    public AiStreamChunk {
        Objects.requireNonNull(content, "Content delta cannot be null");
    }
}
