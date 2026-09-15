package com.chatbot.platform.infrastructure.exception;

/**
 * Exception thrown when authentication or API rate limits are exceeded.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
