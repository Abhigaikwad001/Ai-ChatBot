package com.chatbot.platform.security.jwt;

import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.infrastructure.exception.InvalidTokenException;
import com.chatbot.platform.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtService jwtService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setAccessTokenExpirationMs(3600000L); // 1 hour
        jwtProperties.setIssuer("test-issuer");

        jwtService = new JwtService(jwtProperties);
    }

    private UserPrincipal createPrincipal(UUID id, String email, UserRole role, UserStatus status) {
        User user = new User(email, "hashed_pw", "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        user.setStatus(status);
        return new UserPrincipal(user);
    }

    @Test
    @DisplayName("Should generate token and extract subject email correctly")
    void shouldGenerateTokenAndExtractEmail() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = createPrincipal(userId, "doctor@hospital.org", UserRole.ROLE_USER, UserStatus.ACTIVE);

        String token = jwtService.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("doctor@hospital.org");
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("Should validate token against matching UserDetails")
    void shouldValidateTokenAgainstUserDetails() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = createPrincipal(userId, "doctor@hospital.org", UserRole.ROLE_USER, UserStatus.ACTIVE);

        String token = jwtService.generateToken(principal);

        boolean isValid = jwtService.isTokenValid(token, principal);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject token when email does not match UserDetails")
    void shouldRejectTokenWhenEmailMismatches() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principalA = createPrincipal(userId, "userA@hospital.org", UserRole.ROLE_USER, UserStatus.ACTIVE);
        UserPrincipal principalB = createPrincipal(UUID.randomUUID(), "userB@hospital.org", UserRole.ROLE_USER, UserStatus.ACTIVE);

        String tokenA = jwtService.generateToken(principalA);

        boolean isValid = jwtService.isTokenValid(tokenA, principalB);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should throw InvalidTokenException for malformed token string")
    void shouldThrowExceptionForMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractEmail("invalid.jwt.token"))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Malformed or invalid token");
    }

    @Test
    @DisplayName("Should throw InvalidTokenException for expired token")
    void shouldThrowExceptionForExpiredToken() {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(TEST_SECRET);
        expiredProps.setAccessTokenExpirationMs(-1000L); // Expired immediately
        expiredProps.setIssuer("test-issuer");

        JwtService expiredJwtService = new JwtService(expiredProps);
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = createPrincipal(userId, "expired@hospital.org", UserRole.ROLE_USER, UserStatus.ACTIVE);

        String expiredToken = expiredJwtService.generateToken(principal);

        assertThatThrownBy(() -> expiredJwtService.extractAllClaims(expiredToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Authentication token has expired");
    }
}
