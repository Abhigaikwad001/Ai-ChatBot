package com.chatbot.platform.core.service.ai;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable value object representing a normalized AI completion response.
 * Used as the return type for all concrete implementations of the AiProvider abstraction.
 */
public record AiResponse(
    String content,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    String finishReason,
    Long latencyMs,
    Map<String, Object> metadata
) {
    public AiResponse {
        metadata = (metadata != null) ? Map.copyOf(metadata) : Collections.emptyMap();
        if (totalTokens == null && promptTokens != null && completionTokens != null) {
            totalTokens = promptTokens + completionTokens;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String content;
        private String provider;
        private String model;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String finishReason;
        private Long latencyMs;
        private Map<String, Object> metadata = Collections.emptyMap();

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AiResponse build() {
            return new AiResponse(
                content,
                provider,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                finishReason,
                latencyMs,
                metadata
            );
        }
    }
}
