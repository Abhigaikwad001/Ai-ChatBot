package com.chatbot.platform.infrastructure.exception.ai;

/**
 * Thrown when an AI provider returns HTTP 429 Too Many Requests.
 */
public class AiProviderRateLimitException extends AiProviderException {

    public AiProviderRateLimitException(String providerName, String message) {
        super(providerName, message);
    }

    public AiProviderRateLimitException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
