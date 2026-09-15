package com.chatbot.platform.infrastructure.ai.ollama;

import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaAiProviderTest {

    private AiProperties aiProperties;
    private MockRestServiceServer mockServer;
    private OllamaAiProvider ollamaProvider;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setMaxRetries(0);
        aiProperties.getOllama().setBaseUrl("http://mock-ollama:11434");
        aiProperties.getOllama().setConnectTimeoutMs(1000);
        aiProperties.getOllama().setReadTimeoutMs(2000);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://mock-ollama:11434");
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        this.ollamaProvider = new OllamaAiProvider(aiProperties, restClient);
    }

    @Test
    @DisplayName("1. Successful completion request maps payload and parses Ollama response")
    void testGenerate_successfulResponse() {
        String mockResponseJson = """
                {
                    "model": "llama3.2:3b",
                    "created_at": "2026-09-15T12:00:00Z",
                    "message": {
                        "role": "assistant",
                        "content": "Patient vitals are stable. Continue current observation protocol."
                    },
                    "done_reason": "stop",
                    "done": true,
                    "total_duration": 1200000000,
                    "prompt_eval_count": 24,
                    "eval_count": 48
                }
                """;

        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.model").value("llama3.2:3b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("You are a medical assistant."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Analyze patient vitals."))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .systemPrompt("You are a medical assistant.")
                .messages(List.of(AiMessage.user("Analyze patient vitals.")))
                .build();

        AiResponse response = ollamaProvider.generate(request);

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Patient vitals are stable. Continue current observation protocol.");
        assertThat(response.provider()).isEqualTo("OLLAMA");
        assertThat(response.model()).isEqualTo("llama3.2:3b");
        assertThat(response.promptTokens()).isEqualTo(24);
        assertThat(response.completionTokens()).isEqualTo(48);
        assertThat(response.totalTokens()).isEqualTo(72);
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("2. Injects Authorization Bearer header when API key is configured (Ollama Cloud)")
    void testGenerate_withApiKey_addsBearerHeader() {
        aiProperties.getOllama().setApiKey("ollama-cloud-secret-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://mock-ollama:11434")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getOllama().getApiKey());
        MockRestServiceServer cloudServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        OllamaAiProvider cloudProvider = new OllamaAiProvider(aiProperties, restClient);

        String mockResponseJson = """
                {
                    "model": "llama3.2:3b",
                    "message": { "role": "assistant", "content": "Cloud response" }
                }
                """;

        cloudServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ollama-cloud-secret-key"))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        AiResponse response = cloudProvider.generate(request);

        cloudServer.verify();
        assertThat(response.content()).isEqualTo("Cloud response");
    }

    @Test
    @DisplayName("3. Maps HTTP 401/403 to AiProviderAuthenticationException")
    void testGenerate_401Unauthorized_throwsAuthenticationException() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        assertThatThrownBy(() -> ollamaProvider.generate(request))
                .isInstanceOf(AiProviderAuthenticationException.class)
                .hasMessageContaining("Authentication failed with Ollama provider");
    }

    @Test
    @DisplayName("4. Maps HTTP 429 to AiProviderRateLimitException")
    void testGenerate_429RateLimit_throwsRateLimitException() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        assertThatThrownBy(() -> ollamaProvider.generate(request))
                .isInstanceOf(AiProviderRateLimitException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("5. Maps HTTP 500/503 to AiProviderUnavailableException")
    void testGenerate_500ServerError_throwsUnavailableException() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        assertThatThrownBy(() -> ollamaProvider.generate(request))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessageContaining("Ollama provider server error");
    }

    @Test
    @DisplayName("6. isAvailable checks /api/tags endpoint")
    void testIsAvailable() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/tags"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        assertThat(ollamaProvider.isAvailable()).isTrue();
        mockServer.verify();
    }
}
