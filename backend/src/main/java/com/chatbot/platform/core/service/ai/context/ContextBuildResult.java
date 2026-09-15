package com.chatbot.platform.core.service.ai.context;

import com.chatbot.platform.core.service.ai.AiRequest;

import java.util.Objects;

/**
 * Immutable result of conversation context construction, bundling the finalized
 * {@link AiRequest} along with context metrics and truncation observability metadata.
 *
 * @param aiRequest the provider-neutral AI prompt request ready for dispatch
 * @param totalHistoryMessages total valid messages considered in the conversation
 * @param includedMessages number of messages successfully included in the context window
 * @param omittedMessages number of historical messages omitted due to limits
 * @param estimatedTotalTokens estimated tokens for the entire context (system prompt + messages)
 * @param truncated true if one or more historical turns were pruned to respect limits
 */
public record ContextBuildResult(
    AiRequest aiRequest,
    int totalHistoryMessages,
    int includedMessages,
    int omittedMessages,
    int estimatedTotalTokens,
    boolean truncated
) {
    public ContextBuildResult {
        Objects.requireNonNull(aiRequest, "AiRequest cannot be null");
    }
}
