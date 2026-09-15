package com.chatbot.platform.infrastructure.exception.ai;

/**
 * Thrown when an AI provider is unreachable, offline, or returns 503 Service Unavailable.
 */
public class AiProviderUnavailableException extends AiProviderException {

    public AiProviderUnavailableException(String providerName, String message) {
        super(providerName, message);
    }

    public AiProviderUnavailableException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
