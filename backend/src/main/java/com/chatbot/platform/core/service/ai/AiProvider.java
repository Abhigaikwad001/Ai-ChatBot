package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;

/**
 * Architectural abstraction boundary for AI providers (e.g., Ollama, OpenAI, Anthropic).
 * Concrete implementations communicate with external/local LLM APIs while keeping the
 * core business and persistence layer completely decoupled from provider-specific SDKs.
 */
public interface AiProvider {

    /**
     * Returns the provider type handled by this implementation.
     */
    ProviderType getProviderType();

    /**
     * Generates a chat completion given a provider-neutral AI request.
     *
     * @param request the normalized AI request
     * @return the normalized AI response
     */
    AiResponse generate(AiRequest request);

    /**
     * Probes whether the provider backend is reachable and healthy.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Streams chat completion chunks progressively to the specified stream listener.
     * Default implementation executes synchronous generation and emits a single completion turn
     * for legacy or custom provider stubs.
     *
     * @param request the normalized AI request
     * @param listener callback receiver for stream events
     * @return a cancellation handle for the stream
     */
    default com.chatbot.platform.core.service.ai.stream.AiStreamHandle stream(
        AiRequest request,
        com.chatbot.platform.core.service.ai.stream.AiStreamListener listener
    ) {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            listener.onStart();
            if (!cancelled.get()) {
                AiResponse response = generate(request);
                if (!cancelled.get()) {
                    if (response.content() != null && !response.content().isEmpty()) {
                        listener.onChunk(new com.chatbot.platform.core.service.ai.stream.AiStreamChunk(
                            response.content(), response.model(), 0, true));
                    }
                    listener.onComplete(response);
                }
            }
        } catch (Throwable t) {
            if (!cancelled.get()) {
                listener.onError(t);
            }
        }
        return new com.chatbot.platform.core.service.ai.stream.AiStreamHandle() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }
}
