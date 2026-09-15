package com.chatbot.platform.core.service.ai.stream;

import com.chatbot.platform.core.service.ai.AiResponse;

/**
 * Callback listener interface for receiving asynchronous, progressive AI streaming events.
 */
public interface AiStreamListener {

    /**
     * Called when the upstream stream connection opens successfully.
     */
    void onStart();

    /**
     * Called when the stream handle becomes available to allow early cancellation.
     *
     * @param handle the stream cancellation handle
     */
    default void onHandle(AiStreamHandle handle) {}

    /**
     * Called when a new incremental content chunk is received from the provider.
     *
     * @param chunk the incremental content delta and chunk metadata
     */
    void onChunk(AiStreamChunk chunk);

    /**
     * Called when the generation has finished completely and the final response payload is assembled.
     *
     * @param completeResponse the fully assembled AI response with complete text, usage tokens, and latency
     */
    void onComplete(AiResponse completeResponse);

    /**
     * Called when an unrecoverable error occurs during streaming.
     *
     * @param throwable the error encountered
     */
    void onError(Throwable throwable);
}
