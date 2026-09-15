package com.chatbot.platform.infrastructure.exception.ai;

/**
 * Thrown when an AI provider rejects credentials (401 Unauthorized or 403 Forbidden).
 */
public class AiProviderAuthenticationException extends AiProviderException {

    public AiProviderAuthenticationException(String providerName, String message) {
        super(providerName, message);
    }

    public AiProviderAuthenticationException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
