package com.chatbot.platform.security.util;

import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.security.principal.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Utility methods for retrieving authenticated user context and claims from Spring SecurityContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Retrieves the authenticated UserPrincipal if present in the current SecurityContext.
     */
    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /**
     * Retrieves the authenticated UserPrincipal or throws AccessDeniedException if not authenticated.
     */
    public static UserPrincipal getRequiredCurrentUserPrincipal() {
        return getCurrentUserPrincipal()
            .orElseThrow(() -> new AccessDeniedException("No authenticated user present in SecurityContext"));
    }

    /**
     * Extracts the authenticated User UUID.
     */
    public static UUID getCurrentUserId() {
        return getRequiredCurrentUserPrincipal().getId();
    }

    /**
     * Extracts the authenticated User email.
     */
    public static String getCurrentUserEmail() {
        return getRequiredCurrentUserPrincipal().getEmail();
    }

    /**
     * Checks if the authenticated user possesses the specified role.
     */
    public static boolean hasRole(UserRole role) {
        return getCurrentUserPrincipal()
            .map(p -> p.getRole() == role)
            .orElse(false);
    }
}
