package com.chatbot.platform.infrastructure.ai.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload from Ollama /api/chat completion API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
    @JsonProperty("model") String model,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("message") OllamaResponseMessage message,
    @JsonProperty("done_reason") String doneReason,
    @JsonProperty("done") Boolean done,
    @JsonProperty("total_duration") Long totalDuration,
    @JsonProperty("prompt_eval_count") Integer promptEvalCount,
    @JsonProperty("eval_count") Integer evalCount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaResponseMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
    ) {}
}
