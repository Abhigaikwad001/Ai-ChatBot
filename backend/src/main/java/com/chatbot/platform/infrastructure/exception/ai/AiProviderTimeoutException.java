package com.chatbot.platform.infrastructure.exception.ai;

/**
 * Thrown when an AI provider call exceeds the configured connection or read timeout.
 */
public class AiProviderTimeoutException extends AiProviderException {

    public AiProviderTimeoutException(String providerName, String message) {
        super(providerName, message);
    }

    public AiProviderTimeoutException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
