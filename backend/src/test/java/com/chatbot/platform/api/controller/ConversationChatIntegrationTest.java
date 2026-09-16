package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.conversation.CreateConversationRequest;
import com.chatbot.platform.api.dto.conversation.UpdateConversationRequest;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.security.jwt.JwtService;
import com.chatbot.platform.security.principal.UserPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.chatbot.platform.core.service.ConversationService conversationService;

    @Autowired
    private com.chatbot.platform.core.service.ChatService chatService;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = new User("usera@hospital.org", passwordEncoder.encode("Password123!"), "Dr. Alice");
        userA.setRole(UserRole.ROLE_USER);
        userA.setStatus(UserStatus.ACTIVE);
        userA = userRepository.saveAndFlush(userA);

        userB = new User("userb@hospital.org", passwordEncoder.encode("Password123!"), "Dr. Bob");
        userB.setRole(UserRole.ROLE_USER);
        userB.setStatus(UserStatus.ACTIVE);
        userB = userRepository.saveAndFlush(userB);

        tokenA = jwtService.generateToken(new UserPrincipal(userA));
        tokenB = jwtService.generateToken(new UserPrincipal(userB));
    }

    // =========================================================================
    // CONVERSATIONS: Creation & Retrieval
    // =========================================================================

    @Test
    @DisplayName("1. User can create conversation with default title in standalone mode")
    void testCreateConversation_withDefaultTitle() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.title", is("New conversation")))
            .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("2. Authenticated user can create conversation with custom title")
    void testCreateConversation_withCustomTitle() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest(
            "Cardiology Consult #101",
            "You are a clinical cardiology assistant",
            null,
            null
        );

        mockMvc.perform(post("/api/v1/conversations")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.title", is("Cardiology Consult #101")))
            .andExpect(jsonPath("$.data.systemPrompt", is("You are a clinical cardiology assistant")));
    }

    @Test
    @DisplayName("3. Unauthenticated request to create conversation succeeds in standalone mode")
    void testCreateConversation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("4. User can list conversations in standalone mode and user-scoped via service")
    void testListConversations_returnsOnlyOwnedConversations() throws Exception {
        // Create 2 conversations for User A, 1 for User B
        Conversation c1 = conversationRepository.saveAndFlush(new Conversation(userA, "User A Convo 1"));
        Conversation c2 = conversationRepository.saveAndFlush(new Conversation(userA, "User A Convo 2"));
        conversationRepository.saveAndFlush(new Conversation(userB, "User B Secret Convo"));

        // Standalone endpoint lists all active conversations
        mockMvc.perform(get("/api/v1/conversations")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.totalElements", is(3)))
            .andExpect(jsonPath("$.data.content", hasSize(3)));

        // Service method preserves user isolation
        var userAPage = conversationService.listConversations(userA.getId(), 0, 10);
        assertThat(userAPage.totalElements()).isEqualTo(2);
        assertThat(userAPage.content()).allMatch(c -> c.userId().equals(userA.getId()));
    }

    @Test
    @DisplayName("5. User can retrieve own conversation by ID")
    void testGetConversation_ownConversation_returns200() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Patient Intake"));

        mockMvc.perform(get("/api/v1/conversations/" + convo.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", is(convo.getId().toString())))
            .andExpect(jsonPath("$.data.title", is("Patient Intake")));
    }

    @Test
    @DisplayName("6. Service denies access when retrieving another user's conversation (403 Forbidden)")
    void testGetConversation_otherUsersConversation_returns403() {
        Conversation convoB = conversationRepository.saveAndFlush(new Conversation(userB, "Private Records"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            conversationService.getConversation(convoB.getId(), userA.getId())
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // =========================================================================
    // CONVERSATIONS: Rename, Update & Soft Deletion
    // =========================================================================

    @Test
    @DisplayName("7. User can rename own conversation")
    void testRenameConversation_ownConversation_returns200() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Original Title"));
        UpdateConversationRequest request = new UpdateConversationRequest("Updated Title", null, null, null);

        mockMvc.perform(patch("/api/v1/conversations/" + convo.getId())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.title", is("Updated Title")));

        Conversation updated = conversationRepository.findById(convo.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    @DisplayName("8. Service denies access when renaming another user's conversation (403 Forbidden)")
    void testRenameConversation_otherUsersConversation_returns403() {
        Conversation convoB = conversationRepository.saveAndFlush(new Conversation(userB, "User B Original"));
        UpdateConversationRequest request = new UpdateConversationRequest("Malicious Rename", null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            conversationService.updateConversation(convoB.getId(), userA.getId(), request)
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("9. User can delete/archive own conversation (soft-delete)")
    void testDeleteConversation_ownConversation_softDeletes() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "To Be Deleted"));

        mockMvc.perform(delete("/api/v1/conversations/" + convo.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));

        Conversation deleted = conversationRepository.findById(convo.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(ConversationStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("10. Deleted conversation is excluded from listing and normal retrieval")
    void testDeletedConversation_excludedFromListAndGet() throws Exception {
        Conversation convo = new Conversation(userA, "Deleted Topic");
        convo.softDelete();
        convo = conversationRepository.saveAndFlush(convo);

        // Should not appear in list
        mockMvc.perform(get("/api/v1/conversations")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements", is(0)));

        // Direct GET should return 404 Not Found
        mockMvc.perform(get("/api/v1/conversations/" + convo.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("deleted")));
    }

    @Test
    @DisplayName("11. Service denies access when deleting another user's conversation (403 Forbidden)")
    void testDeleteConversation_otherUsersConversation_returns403() {
        Conversation convoB = conversationRepository.saveAndFlush(new Conversation(userB, "User B Safe"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            conversationService.deleteConversation(convoB.getId(), userA.getId())
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        Conversation intact = conversationRepository.findById(convoB.getId()).orElseThrow();
        assertThat(intact.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    }

    // =========================================================================
    // MESSAGES: Submission & Retrieval
    // =========================================================================

    @Test
    @DisplayName("12. User can send a message to own conversation")
    void testSendMessage_ownConversation_returns201() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Oncology Chat"));
        SendMessageRequest request = new SendMessageRequest("Patient presents with stage 2 hypertension.", null);

        mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.sequenceNumber", is(2)))
            .andExpect(jsonPath("$.data.role", is("ASSISTANT")))
            .andExpect(jsonPath("$.data.status", is("SENT")));
    }

    @Test
    @DisplayName("13. Unauthenticated request to send message succeeds in standalone mode")
    void testSendMessage_unauthenticated_returns401() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Topic"));
        SendMessageRequest request = new SendMessageRequest("Hello", null);

        mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.role", is("ASSISTANT")));
    }

    @Test
    @DisplayName("14. Service denies access when sending message to another user's conversation (403 Forbidden)")
    void testSendMessage_otherUsersConversation_returns403() {
        Conversation convoB = conversationRepository.saveAndFlush(new Conversation(userB, "Private Diary"));
        SendMessageRequest request = new SendMessageRequest("Attempted intrusion", null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            chatService.sendMessage(convoB.getId(), userA.getId(), request)
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("15. Blank or whitespace message is rejected with 400 Bad Request")
    void testSendMessage_blankContent_returns400() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Chat"));
        SendMessageRequest request = new SendMessageRequest("    ", null);

        mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("16. Oversized message content (>10,000 chars) is rejected with 400 Bad Request")
    void testSendMessage_oversizedContent_returns400() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Chat"));
        String hugeContent = "A".repeat(10001);
        SendMessageRequest request = new SendMessageRequest(hugeContent, null);

        mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("17. Messages are returned in deterministic sequential order")
    void testGetMessages_deterministicOrdering() throws Exception {
        Conversation convo = conversationRepository.saveAndFlush(new Conversation(userA, "Sequencing Test"));

        // Send 3 consecutive messages
        for (int i = 1; i <= 3; i++) {
            SendMessageRequest req = new SendMessageRequest("Turn " + i, null);
            mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        }

        // Retrieve messages
        mockMvc.perform(get("/api/v1/conversations/" + convo.getId() + "/messages")
                .header("Authorization", "Bearer " + tokenA)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.totalElements", is(6)))
            .andExpect(jsonPath("$.data.content[0].sequenceNumber", is(1)))
            .andExpect(jsonPath("$.data.content[0].content", is("Turn 1")))
            .andExpect(jsonPath("$.data.content[1].sequenceNumber", is(2)))
            .andExpect(jsonPath("$.data.content[1].role", is("ASSISTANT")))
            .andExpect(jsonPath("$.data.content[2].sequenceNumber", is(3)))
            .andExpect(jsonPath("$.data.content[2].content", is("Turn 2")))
            .andExpect(jsonPath("$.data.content[3].sequenceNumber", is(4)))
            .andExpect(jsonPath("$.data.content[3].role", is("ASSISTANT")))
            .andExpect(jsonPath("$.data.content[4].sequenceNumber", is(5)))
            .andExpect(jsonPath("$.data.content[4].content", is("Turn 3")))
            .andExpect(jsonPath("$.data.content[5].sequenceNumber", is(6)))
            .andExpect(jsonPath("$.data.content[5].role", is("ASSISTANT")));
    }

    @Test
    @DisplayName("18. Service denies access when retrieving another user's conversation messages (403 Forbidden)")
    void testGetMessages_otherUsersConversation_returns403() {
        Conversation convoB = conversationRepository.saveAndFlush(new Conversation(userB, "User B Secrets"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            chatService.getMessages(convoB.getId(), userA.getId(), 0, 10)
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("19. Deleted conversation cannot receive new messages (400 Bad Request)")
    void testSendMessage_deletedConversation_returns400() throws Exception {
        Conversation convo = new Conversation(userA, "Archived Topic");
        convo.softDelete();
        convo = conversationRepository.saveAndFlush(convo);

        SendMessageRequest request = new SendMessageRequest("Trying to post", null);

        mockMvc.perform(post("/api/v1/conversations/" + convo.getId() + "/messages")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("deleted")));
    }
}
