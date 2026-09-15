package com.chatbot.platform.core.service.ai.context;

import com.chatbot.platform.core.service.ai.AiMessage;

/**
 * Provider-neutral abstraction for estimating token counts in prompts, system instructions,
 * and conversational turns.
 *
 * <p>Allows initial heuristic approximations while preserving an architecture where
 * precise model-specific tokenizers (e.g. tiktoken, Llama BPE) can be injected later
 * without modifying domain or chat orchestration services.
 */
public interface TokenEstimator {

    /**
     * Estimates the token count of a raw text string.
     *
     * @param text the text to estimate
     * @return non-negative estimated token count
     */
    int estimateTokens(String text);

    /**
     * Estimates the total token count of an {@link AiMessage}, accounting for both its
     * textual content and provider framing/role overhead (e.g. delimiters, role tokens).
     *
     * @param message the AI message turn
     * @return non-negative estimated token count including framing overhead
     */
    int estimateMessageTokens(AiMessage message);
}
