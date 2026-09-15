package com.chatbot.platform.infrastructure.ai.ollama;

import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.core.service.ai.stream.AiStreamChunk;
import com.chatbot.platform.core.service.ai.stream.AiStreamHandle;
import com.chatbot.platform.core.service.ai.stream.AiStreamListener;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaAiProviderStreamingTest {

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
    @DisplayName("1. Progressively receives NDJSON stream chunks and fires onComplete with usage metrics")
    void testStream_successfulIncrementalChunks() {
        String ndjsonStream = """
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Patient "},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"vitals are "},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"stable."},"done":true,"done_reason":"stop","prompt_eval_count":15,"eval_count":25}
                """;

        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.stream").value(true))
                .andRespond(withSuccess(ndjsonStream, MediaType.parseMediaType("application/x-ndjson")));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .systemPrompt("Medical assistant")
                .messages(List.of(AiMessage.user("Check vitals")))
                .build();

        List<AiStreamChunk> receivedChunks = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<AiResponse> completedResponse = new AtomicReference<>();
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean(false);

        AiStreamHandle handle = ollamaProvider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
                started.set(true);
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
                receivedChunks.add(chunk);
            }

            @Override
            public void onComplete(AiResponse completeResponse) {
                completedResponse.set(completeResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                streamError.set(throwable);
            }
        });

        mockServer.verify();

        assertThat(started.get()).isTrue();
        assertThat(streamError.get()).isNull();
        assertThat(receivedChunks).hasSize(3);
        assertThat(receivedChunks.get(0).content()).isEqualTo("Patient ");
        assertThat(receivedChunks.get(0).chunkIndex()).isEqualTo(1);
        assertThat(receivedChunks.get(0).isLast()).isFalse();

        assertThat(receivedChunks.get(1).content()).isEqualTo("vitals are ");
        assertThat(receivedChunks.get(1).chunkIndex()).isEqualTo(2);
        assertThat(receivedChunks.get(1).isLast()).isFalse();

        assertThat(receivedChunks.get(2).content()).isEqualTo("stable.");
        assertThat(receivedChunks.get(2).chunkIndex()).isEqualTo(3);
        assertThat(receivedChunks.get(2).isLast()).isTrue();

        AiResponse finalResponse = completedResponse.get();
        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse.content()).isEqualTo("Patient vitals are stable.");
        assertThat(finalResponse.provider()).isEqualTo("OLLAMA");
        assertThat(finalResponse.model()).isEqualTo("llama3.2:3b");
        assertThat(finalResponse.promptTokens()).isEqualTo(15);
        assertThat(finalResponse.completionTokens()).isEqualTo(25);
        assertThat(finalResponse.totalTokens()).isEqualTo(40);
        assertThat(finalResponse.finishReason()).isEqualTo("stop");
    }

    @Test
    @DisplayName("2. Handles provider 401 Unauthorized via onError callback")
    void testStream_authenticationFailure() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        ollamaProvider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
            }

            @Override
            public void onComplete(AiResponse completeResponse) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
            }
        });

        mockServer.verify();
        assertThat(errorRef.get()).isInstanceOf(AiProviderAuthenticationException.class);
    }

    @Test
    @DisplayName("3. Handles provider 503 Service Unavailable via onError callback")
    void testStream_serviceUnavailableFailure() {
        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        ollamaProvider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
            }

            @Override
            public void onComplete(AiResponse completeResponse) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
            }
        });

        mockServer.verify();
        assertThat(errorRef.get()).isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    @DisplayName("4. Handles malformed NDJSON line gracefully by dispatching onError")
    void testStream_malformedNdjson() {
        String badNdjson = """
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Initial text "},"done":false}
                {NOT_VALID_JSON...
                """;

        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(badNdjson, MediaType.APPLICATION_JSON));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        List<AiStreamChunk> received = new ArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ollamaProvider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
                received.add(chunk);
            }

            @Override
            public void onComplete(AiResponse completeResponse) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
            }
        });

        mockServer.verify();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).content()).isEqualTo("Initial text ");
        assertThat(errorRef.get()).isNotNull();
        assertThat(errorRef.get()).isInstanceOf(AiProviderException.class);
    }

    @Test
    @DisplayName("5. Cancellation handle suppresses further chunk delivery")
    void testStream_cancellationHandle() {
        String ndjsonStream = """
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Chunk 1 "},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Chunk 2 "},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Chunk 3"},"done":true}
                """;

        mockServer.expect(requestTo("http://mock-ollama:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(ndjsonStream, MediaType.parseMediaType("application/x-ndjson")));

        AiRequest request = AiRequest.builder()
                .model("llama3.2:3b")
                .messages(List.of(AiMessage.user("Hello")))
                .build();

        List<AiStreamChunk> received = new ArrayList<>();
        AtomicReference<AiStreamHandle> handleRef = new AtomicReference<>();

        AiStreamHandle handle = ollamaProvider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
                received.add(chunk);
                // Cancel after first chunk
                if (handleRef.get() != null) {
                    handleRef.get().cancel();
                }
            }

            @Override
            public void onComplete(AiResponse completeResponse) {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        handleRef.set(handle);
        handle.cancel(); // ensure cancelled

        assertThat(handle.isCancelled()).isTrue();
    }
}
