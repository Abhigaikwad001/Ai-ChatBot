package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.conversation.CreateConversationRequest;
import com.chatbot.platform.api.dto.conversation.UpdateConversationRequest;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.infrastructure.ai.TestStubAiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests verifying that the standalone chatbot APIs function
 * completely without JWT authentication, authorization headers, or fake users.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StandaloneNoAuthChatbotIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestStubAiProvider stubAiProvider;

    @BeforeEach
    void setUp() {
        stubAiProvider.reset();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Conversation can be created without JWT or Authorization header")
    void testCreateConversation_withoutAuth_succeeds() throws Exception {
        CreateConversationRequest req = new CreateConversationRequest("Standalone No-Auth Chat", "You are a helpful assistant", null, null);

        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.userId", nullValue()))
            .andExpect(jsonPath("$.data.title", is("Standalone No-Auth Chat")))
            .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        List<Conversation> all = conversationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUser()).isNull();
        assertThat(all.get(0).getTitle()).isEqualTo("Standalone No-Auth Chat");
    }

    @Test
    @DisplayName("2. Conversations can be listed without JWT")
    void testListConversations_withoutAuth_succeeds() throws Exception {
        Conversation c1 = conversationRepository.saveAndFlush(new Conversation("Topic 1"));
        Conversation c2 = conversationRepository.saveAndFlush(new Conversation("Topic 2"));

        mockMvc.perform(get("/api/v1/conversations")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.totalElements", is(2)))
            .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("3. Conversation detail can be retrieved by ID without JWT")
    void testGetConversation_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("Medical Advice"));

        mockMvc.perform(get("/api/v1/conversations/" + c.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", is(c.getId().toString())))
            .andExpect(jsonPath("$.data.title", is("Medical Advice")));
    }

    @Test
    @DisplayName("4. Conversation can be renamed without JWT")
    void testRenameConversation_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("Old Name"));
        UpdateConversationRequest req = new UpdateConversationRequest("New Name", null, null, null);

        mockMvc.perform(patch("/api/v1/conversations/" + c.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.title", is("New Name")));

        Conversation updated = conversationRepository.findById(c.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("5. Conversation can be deleted without JWT")
    void testDeleteConversation_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("To Delete"));

        mockMvc.perform(delete("/api/v1/conversations/" + c.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));

        Conversation deleted = conversationRepository.findById(c.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(ConversationStatus.DELETED);
    }

    @Test
    @DisplayName("6. Normal message sending works without JWT")
    void testSendMessage_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("Chat Turn"));
        SendMessageRequest req = new SendMessageRequest("Explain Java virtual threads", null);

        mockMvc.perform(post("/api/v1/conversations/" + c.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.role", is("ASSISTANT")))
            .andExpect(jsonPath("$.data.status", is("SENT")));

        List<Message> msgs = messageRepository.findByConversationIdOrderBySequenceNumberAsc(c.getId());
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(msgs.get(0).getContent()).isEqualTo("Explain Java virtual threads");
        assertThat(msgs.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    @DisplayName("7. Message history can be retrieved without JWT")
    void testGetMessages_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("History Chat"));
        Message m1 = new Message(c, 1, MessageRole.USER, "First question", MessageStatus.SENT);
        Message m2 = new Message(c, 2, MessageRole.ASSISTANT, "First answer", MessageStatus.SENT);
        messageRepository.saveAllAndFlush(List.of(m1, m2));

        mockMvc.perform(get("/api/v1/conversations/" + c.getId() + "/messages")
                .param("page", "0")
                .param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.totalElements", is(2)))
            .andExpect(jsonPath("$.data.content", hasSize(2)))
            .andExpect(jsonPath("$.data.content[0].content", is("First question")))
            .andExpect(jsonPath("$.data.content[1].content", is("First answer")));
    }

    @Test
    @DisplayName("8. SSE streaming endpoint works without JWT")
    void testStreamMessage_withoutAuth_succeeds() throws Exception {
        Conversation c = conversationRepository.saveAndFlush(new Conversation("Streaming Chat"));
        stubAiProvider.setCustomChunks(List.of("Java is ", "a multiplatform language."));

        SendMessageRequest req = new SendMessageRequest("Explain Java simply", null);

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/" + c.getId() + "/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        // Await async stream completion and verify messages persisted in database
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(c.getId());
            assertThat(allMessages).hasSize(2);
            assertThat(allMessages.get(0).getRole()).isEqualTo(MessageRole.USER);
            assertThat(allMessages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(allMessages.get(1).getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(allMessages.get(1).getContent()).isEqualTo("Java is a multiplatform language.");
        });
    }

    @Test
    @DisplayName("9. No fake authentication or mock user is injected into SecurityContext")
    void testNoFakeAuthenticationInSecurityContext() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
