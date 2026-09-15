package com.chatbot.platform.infrastructure.exception;

/**
 * Exception thrown when an operation is attempted on a soft-deleted conversation.
 */
public class ConversationDeletedException extends RuntimeException {

    public ConversationDeletedException(String message) {
        super(message);
    }
}
