package com.chatbot.platform.security;

import com.chatbot.platform.api.dto.auth.LoginRequest;
import com.chatbot.platform.api.dto.auth.RegisterRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.security.jwt.JwtProperties;
import com.chatbot.platform.security.jwt.JwtService;
import com.chatbot.platform.security.principal.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import com.chatbot.platform.security.util.OwnershipValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private OwnershipValidator ownershipValidator;

    @BeforeEach
    void cleanDatabase() {
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email, String rawPassword, UserRole role, UserStatus status) {
        User user = new User(email.toLowerCase(), passwordEncoder.encode(rawPassword), "Test User " + email);
        user.setRole(role);
        user.setStatus(status);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("1. Successful user registration should persist user and return 201 Created without sensitive fields")
    void testSuccessfulRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser@hospital.org", "SecurePassword123!", "New Staff");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.email", is("newuser@hospital.org")))
            .andExpect(jsonPath("$.data.fullName", is("New Staff")))
            .andExpect(jsonPath("$.data.role", is("ROLE_USER")))
            .andExpect(jsonPath("$.data.status", is("ACTIVE")))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        assertThat(userRepository.findByEmail("newuser@hospital.org")).isPresent();
    }

    @Test
    @DisplayName("2. Duplicate email registration should return 409 Conflict")
    void testDuplicateEmailRegistration() throws Exception {
        createAndPersistUser("existing@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        // Case-insensitive duplicate test
        RegisterRequest request = new RegisterRequest("EXISTING@hospital.org", "DifferentPass123!", "Duplicate User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("3. Registration with invalid payload (short password, invalid email) should return 400 Bad Request")
    void testRegistrationWithValidationErrors() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "short", "");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.data.email", notNullValue()))
            .andExpect(jsonPath("$.data.password", notNullValue()))
            .andExpect(jsonPath("$.data.fullName", notNullValue()));
    }

    @Test
    @DisplayName("4. Successful login returns 200 OK with signed JWT and user details")
    void testSuccessfulLogin() throws Exception {
        createAndPersistUser("doctor@hospital.org", "DoctorSecret123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        LoginRequest request = new LoginRequest("doctor@hospital.org", "DoctorSecret123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.data.accessToken", notNullValue()))
            .andExpect(jsonPath("$.data.user.email", is("doctor@hospital.org")))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();

        // Verify token validity
        assertThat(jwtService.extractEmail(token)).isEqualTo("doctor@hospital.org");
    }

    @Test
    @DisplayName("5. Invalid password login returns 401 Unauthorized with generic message to prevent enumeration")
    void testLoginWithInvalidPassword() throws Exception {
        createAndPersistUser("doctor@hospital.org", "CorrectPassword123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        LoginRequest request = new LoginRequest("doctor@hospital.org", "WrongPassword999!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("6. Non-existent email login returns 401 Unauthorized with same generic message")
    void testLoginWithNonExistentEmail() throws Exception {
        LoginRequest request = new LoginRequest("ghost@hospital.org", "SomePassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("7. Suspended account login returns 403 Forbidden")
    void testLoginWithSuspendedAccount() throws Exception {
        createAndPersistUser("suspended@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.SUSPENDED);

        LoginRequest request = new LoginRequest("suspended@hospital.org", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("suspended")));
    }

    @Test
    @DisplayName("8. Deactivated account login returns 403 Forbidden")
    void testLoginWithDeactivatedAccount() throws Exception {
        createAndPersistUser("deactivated@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.DEACTIVATED);

        LoginRequest request = new LoginRequest("deactivated@hospital.org", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("deactivated")));
    }

    @Test
    @DisplayName("9. Protected endpoint without Authorization header returns 401 Unauthorized")
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("Full authentication is required")));
    }

    @Test
    @DisplayName("10. Protected endpoint with malformed/invalid JWT returns 401 Unauthorized")
    void testProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer this.is.invalid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("Authentication failed")));
    }

    @Test
    @DisplayName("11. Protected endpoint with expired JWT returns 401 Unauthorized")
    void testProtectedEndpointWithExpiredToken() throws Exception {
        User user = createAndPersistUser("user@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        // Create expired token
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(jwtProperties.getSecret());
        expiredProps.setAccessTokenExpirationMs(-5000L);
        expiredProps.setIssuer(jwtProperties.getIssuer());

        JwtService expiredService = new JwtService(expiredProps);
        String expiredToken = expiredService.generateToken(new UserPrincipal(user));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("12. Protected endpoint with valid JWT returns 200 OK and current user profile")
    void testProtectedEndpointWithValidToken() throws Exception {
        User user = createAndPersistUser("verified@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);
        String token = jwtService.generateToken(new UserPrincipal(user));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.email", is("verified@hospital.org")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("13. Regular user accessing admin endpoint returns 403 Forbidden")
    void testRoleBasedAuthorization_regularUserDeniedAdminAccess() throws Exception {
        User regularUser = createAndPersistUser("staff@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);
        String token = jwtService.generateToken(new UserPrincipal(regularUser));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("Access denied")));
    }

    @Test
    @DisplayName("14. Admin user accessing admin endpoint returns 200 OK")
    void testRoleBasedAuthorization_adminUserAllowedAdminAccess() throws Exception {
        User adminUser = createAndPersistUser("admin@hospital.org", "AdminPassword123!", UserRole.ROLE_ADMIN, UserStatus.ACTIVE);
        String token = jwtService.generateToken(new UserPrincipal(adminUser));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.adminEmail", is("admin@hospital.org")));
    }

    @Test
    @DisplayName("15. Ownership boundary: User A cannot access User B's conversation via OwnershipValidator (throws AccessDeniedException)")
    void testOwnershipBoundary_crossUserAccessDenied() {
        User userA = createAndPersistUser("userA@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);
        User userB = createAndPersistUser("userB@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        // User B creates a conversation
        Conversation convoB = new Conversation(userB, "Private Medical History");
        convoB = conversationRepository.saveAndFlush(convoB);

        // Direct call to OwnershipValidator asserting ownership validation throws 403 AccessDeniedException
        UUID convoBId = convoB.getId();
        UUID userAId = userA.getId();
        assertThatThrownBy(() -> ownershipValidator.verifyAndGetConversation(convoBId, userAId))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("16. Ownership boundary: Owner can access own conversation via OwnershipValidator")
    void testOwnershipBoundary_ownerCanAccessOwnConversation() {
        User userA = createAndPersistUser("userA@hospital.org", "Password123!", UserRole.ROLE_USER, UserStatus.ACTIVE);

        // User A creates a conversation
        Conversation convoA = new Conversation(userA, "User A Patient Notes");
        convoA = conversationRepository.saveAndFlush(convoA);

        Conversation verified = ownershipValidator.verifyAndGetConversation(convoA.getId(), userA.getId());
        assertThat(verified).isNotNull();
        assertThat(verified.getTitle()).isEqualTo("User A Patient Notes");
    }
}
