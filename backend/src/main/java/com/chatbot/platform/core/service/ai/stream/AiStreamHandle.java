package com.chatbot.platform.core.service.ai.stream;

/**
 * Handle allowing callers to cancel an active provider AI stream.
 */
public interface AiStreamHandle {

    /**
     * Signals cancellation to the underlying provider stream reader.
     */
    void cancel();

    /**
     * Returns true if cancellation has been requested.
     */
    boolean isCancelled();
}
