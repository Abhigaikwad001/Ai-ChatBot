package com.chatbot.platform.core.service.ai;

import java.util.Objects;

/**
 * Immutable provider-neutral representation of a single message turn in an AI dialogue context.
 */
public record AiMessage(
    AiRole role,
    String content
) {
    public AiMessage {
        Objects.requireNonNull(role, "AiRole cannot be null");
        Objects.requireNonNull(content, "AiMessage content cannot be null");
    }

    public static AiMessage system(String content) {
        return new AiMessage(AiRole.SYSTEM, content);
    }

    public static AiMessage user(String content) {
        return new AiMessage(AiRole.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(AiRole.ASSISTANT, content);
    }
}
