package com.chatbot.platform.core.service.ai;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral prompt and parameter specification dispatched to an
 * AIProvider.
 */
public record AiRequest(
        String model,
        String systemPrompt,
        List<AiMessage> messages,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Map<String, Object> options) {
    public AiRequest {
        Objects.requireNonNull(messages, "Messages list cannot be null");
        messages = List.copyOf(messages);
        options = (options != null) ? Map.copyOf(options) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model = "llama3.2:3b";
        private String systemPrompt;
        private List<AiMessage> messages = Collections.emptyList();
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
        private Double topP = 1.0;
        private Map<String, Object> options = Collections.emptyMap();

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder messages(List<AiMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }

        public AiRequest build() {
            return new AiRequest(model, systemPrompt, messages, temperature, maxTokens, topP, options);
        }
    }
}
