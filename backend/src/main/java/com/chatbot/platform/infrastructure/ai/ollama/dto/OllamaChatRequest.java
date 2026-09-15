package com.chatbot.platform.infrastructure.ai.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request payload for Ollama /api/chat completion API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
    @JsonProperty("model") String model,
    @JsonProperty("messages") List<OllamaMessageDto> messages,
    @JsonProperty("stream") boolean stream,
    @JsonProperty("options") Map<String, Object> options
) {
    public record OllamaMessageDto(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
    ) {}
}
