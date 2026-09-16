package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageStreamingIntegrationTest {

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
    private TestStubAiProvider stubAiProvider;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Conversation conversationA;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = new User("doctor_a@hospital.org", passwordEncoder.encode("Password123!"), "Dr. Alice");
        userA.setRole(UserRole.ROLE_USER);
        userA.setStatus(UserStatus.ACTIVE);
        userA = userRepository.saveAndFlush(userA);

        userB = new User("doctor_b@hospital.org", passwordEncoder.encode("Password123!"), "Dr. Bob");
        userB.setRole(UserRole.ROLE_USER);
        userB.setStatus(UserStatus.ACTIVE);
        userB = userRepository.saveAndFlush(userB);

        tokenA = jwtService.generateToken(new UserPrincipal(userA));
        tokenB = jwtService.generateToken(new UserPrincipal(userB));

        conversationA = new Conversation(userA, "Cardiology Rounds");
        conversationA = conversationRepository.saveAndFlush(conversationA);
    }

    @Test
    @DisplayName("1. POST /stream connects successfully, returns text/event-stream, and persists messages")
    void testStreamMessage_success() throws Exception {
        stubAiProvider.setCustomChunks(List.of("Blood pressure is ", "120/80 mmHg."));

        SendMessageRequest request = new SendMessageRequest("Analyze BP reading", null);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/conversations/" + conversationA.getId() + "/messages/stream")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
            .andReturn();

        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);

        // Verify that USER message was persisted in Transaction 1
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getContent()).isEqualTo("Analyze BP reading");
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);

        // Await completion of generation and persistence of ASSISTANT message in Transaction 2
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
            assertThat(allMessages).hasSize(2);
            Message assistantMsg = allMessages.get(1);
            assertThat(assistantMsg.getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(assistantMsg.getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(assistantMsg.getContent()).isEqualTo("Blood pressure is 120/80 mmHg.");
        });
    }

    @Autowired
    private com.chatbot.platform.core.service.ChatService chatService;

    @Test
    @DisplayName("2. POST /stream connects successfully for unauthenticated user in standalone mode")
    void testStreamMessage_unauthenticated_succeedsInStandaloneMode() throws Exception {
        stubAiProvider.setCustomChunks(List.of("Unauthenticated ", "streaming works!"));
        SendMessageRequest request = new SendMessageRequest("Unauthenticated message", null);

        mockMvc.perform(post("/api/v1/conversations/" + conversationA.getId() + "/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));
    }

    @Test
    @DisplayName("3. ChatService rejects unauthorized user when userId is provided")
    void testStreamMessage_serviceRejectsWrongOwnerWhenUserIdProvided() {
        SendMessageRequest request = new SendMessageRequest("Wrong owner message", null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            chatService.streamMessage(conversationA.getId(), userB.getId(), request)
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("4. POST /stream returns 404 for non-existent conversation")
    void testStreamMessage_nonExistentConversation() throws Exception {
        SendMessageRequest request = new SendMessageRequest("Hello non-existent", null);

        mockMvc.perform(post("/api/v1/conversations/" + UUID.randomUUID() + "/messages/stream")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. POST /stream rejects deleted conversation with 400/409 error")
    void testStreamMessage_deletedConversation() throws Exception {
        conversationA.setStatus(ConversationStatus.DELETED);
        conversationRepository.saveAndFlush(conversationA);

        SendMessageRequest request = new SendMessageRequest("Message to deleted convo", null);

        mockMvc.perform(post("/api/v1/conversations/" + conversationA.getId() + "/messages/stream")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. POST /stream rejects oversized message with 400 Bad Request before streaming starts")
    void testStreamMessage_oversizedMessage_rejected() throws Exception {
        String massivePrompt = "Z".repeat(25000);
        SendMessageRequest request = new SendMessageRequest(massivePrompt, null);

        mockMvc.perform(post("/api/v1/conversations/" + conversationA.getId() + "/messages/stream")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        // Ensure no messages were saved
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationA.getId());
        assertThat(messages).isEmpty();
    }
}
