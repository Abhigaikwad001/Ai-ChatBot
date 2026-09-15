package com.chatbot.platform.infrastructure.exception.ai;

/**
 * Base unchecked exception for AI provider errors.
 */
public class AiProviderException extends RuntimeException {

    private final String providerName;

    public AiProviderException(String message) {
        super(message);
        this.providerName = "UNKNOWN";
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.providerName = "UNKNOWN";
    }

    public AiProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public AiProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
