package com.chatbot.platform.api.dto.stream;

/**
 * SSE payload emitted on periodic {@code heartbeat} events to maintain long-lived connections.
 */
public record HeartbeatEvent(
    long timestamp
) {
    public HeartbeatEvent() {
        this(System.currentTimeMillis());
    }
}
