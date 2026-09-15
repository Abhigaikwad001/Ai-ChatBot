package com.chatbot.platform.api.dto.user;

import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
