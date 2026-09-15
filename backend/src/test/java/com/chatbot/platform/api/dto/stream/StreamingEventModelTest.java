package com.chatbot.platform.api.dto.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingEventModelTest {

    @Test
    @DisplayName("1. StreamEventType constants follow standard SSE naming conventions")
    void testStreamEventTypeConstants() {
        assertThat(StreamEventType.MESSAGE_START).isEqualTo("message_start");
        assertThat(StreamEventType.CONTENT).isEqualTo("content");
        assertThat(StreamEventType.HEARTBEAT).isEqualTo("heartbeat");
        assertThat(StreamEventType.MESSAGE_COMPLETE).isEqualTo("message_complete");
        assertThat(StreamEventType.ERROR).isEqualTo("error");
    }

    @Test
    @DisplayName("2. MessageStartEvent immutably retains conversationId and role")
    void testMessageStartEvent() {
        UUID conversationId = UUID.randomUUID();
        MessageStartEvent event = new MessageStartEvent(conversationId, "assistant");

        assertThat(event.conversationId()).isEqualTo(conversationId);
        assertThat(event.role()).isEqualTo("assistant");
    }

    @Test
    @DisplayName("3. ContentChunkEvent holds content delta and sequence number")
    void testContentChunkEvent() {
        ContentChunkEvent event = new ContentChunkEvent("Hello world", 42);

        assertThat(event.content()).isEqualTo("Hello world");
        assertThat(event.sequence()).isEqualTo(42);
    }

    @Test
    @DisplayName("4. MessageCompleteEvent aggregates all generation metadata and token metrics")
    void testMessageCompleteEvent() {
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        MessageCompleteEvent event = new MessageCompleteEvent(
                messageId,
                conversationId,
                "assistant",
                "OLLAMA",
                "llama3.2:3b",
                "stop",
                12,
                24,
                36,
                150L,
                35L);

        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.conversationId()).isEqualTo(conversationId);
        assertThat(event.role()).isEqualTo("assistant");
        assertThat(event.provider()).isEqualTo("OLLAMA");
        assertThat(event.model()).isEqualTo("llama3.2:3b");
        assertThat(event.finishReason()).isEqualTo("stop");
        assertThat(event.promptTokens()).isEqualTo(12);
        assertThat(event.completionTokens()).isEqualTo(24);
        assertThat(event.totalTokens()).isEqualTo(36);
        assertThat(event.latencyMs()).isEqualTo(150L);
        assertThat(event.timeToFirstChunkMs()).isEqualTo(35L);
    }

    @Test
    @DisplayName("5. StreamErrorEvent holds sanitized error code and user-facing message")
    void testStreamErrorEvent() {
        StreamErrorEvent event = new StreamErrorEvent("AI_PROVIDER_UNAVAILABLE", "AI service offline");

        assertThat(event.code()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
        assertThat(event.message()).isEqualTo("AI service offline");
    }

    @Test
    @DisplayName("6. HeartbeatEvent creates timestamped keep-alive payload")
    void testHeartbeatEvent() {
        long before = System.currentTimeMillis();
        HeartbeatEvent event = new HeartbeatEvent();
        long after = System.currentTimeMillis();

        assertThat(event.timestamp()).isBetween(before, after);
    }
}
