package com.chatbot.platform.api.dto.stream;

/**
 * Standardized Server-Sent Event (SSE) event names emitted to clients.
 */
public final class StreamEventType {

    public static final String MESSAGE_START = "message_start";
    public static final String CONTENT = "content";
    public static final String HEARTBEAT = "heartbeat";
    public static final String MESSAGE_COMPLETE = "message_complete";
    public static final String ERROR = "error";

    private StreamEventType() {
        // Prevent instantiation
    }
}
