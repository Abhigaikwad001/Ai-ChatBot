package com.chatbot.platform.api.dto.auth;

import com.chatbot.platform.api.dto.user.UserResponse;

/**
 * Immutable DTO record returned upon successful authentication.
 */
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInMs,
    UserResponse user
) {
    public static AuthResponse of(String accessToken, long expiresInMs, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs, user);
    }
}
