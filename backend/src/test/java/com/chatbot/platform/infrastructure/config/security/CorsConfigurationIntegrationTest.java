package com.chatbot.platform.infrastructure.config.security;

import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.core.domain.entity.Conversation;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String DISALLOWED_ORIGIN = "http://malicious-site.com";

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
    }

    @Test
    @DisplayName("REST: GET /api/v1/conversations returns CORS headers for allowed origin http://localhost:4200")
    void testGetConversations_corsAllowedOrigin_succeeds() throws Exception {
        mockMvc.perform(get("/api/v1/conversations")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("REST: GET /api/v1/conversations rejects disallowed origin")
    void testGetConversations_corsDisallowedOrigin_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/conversations")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SSE Preflight: OPTIONS /api/v1/conversations/{id}/messages/stream permits POST from http://localhost:4200")
    void testSseStreamPreflight_corsAllowedOrigin_succeeds() throws Exception {
        Conversation conv = conversationRepository.saveAndFlush(new Conversation("SSE CORS Preflight"));

        mockMvc.perform(options("/api/v1/conversations/" + conv.getId() + "/messages/stream")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, accept"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")));
    }

    @Test
    @DisplayName("SSE Streaming: POST /api/v1/conversations/{id}/messages/stream returns CORS headers for http://localhost:4200")
    void testSseStreamMessage_corsAllowedOrigin_succeeds() throws Exception {
        Conversation conv = conversationRepository.saveAndFlush(new Conversation("SSE Stream"));
        SendMessageRequest request = new SendMessageRequest("Hello AI", null);

        mockMvc.perform(post("/api/v1/conversations/" + conv.getId() + "/messages/stream")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
