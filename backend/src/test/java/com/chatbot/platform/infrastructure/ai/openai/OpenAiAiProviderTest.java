package com.chatbot.platform.infrastructure.ai.openai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.core.service.ai.stream.AiStreamChunk;
import com.chatbot.platform.core.service.ai.stream.AiStreamHandle;
import com.chatbot.platform.core.service.ai.stream.AiStreamListener;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import com.openai.client.OpenAIClient;
import com.openai.core.http.Headers;
import com.openai.core.http.StreamResponse;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionChunk;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessage;
import com.openai.models.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.ModelService;
import com.openai.services.blocking.chat.CompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiAiProviderTest {

    private OpenAIClient client;
    private ChatService chatService;
    private CompletionService completionService;
    private ModelService modelService;
    private AiProperties aiProperties;
    private OpenAiAiProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(OpenAIClient.class);
        chatService = mock(ChatService.class);
        completionService = mock(CompletionService.class);
        modelService = mock(ModelService.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(client.models()).thenReturn(modelService);

        aiProperties = new AiProperties();
        aiProperties.setDefaultProvider(ProviderType.OPENAI);
        aiProperties.setDefaultModel("gpt-4o-mini");

        provider = new OpenAiAiProvider(aiProperties, client);
    }

    @Test
    @DisplayName("1. Returns ProviderType.OPENAI")
    void testGetProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.OPENAI);
    }

    @Test
    @DisplayName("2. Normal synchronous generation maps request, parameters, and parses response")
    void testGenerate_success() {
        ChatCompletion mockCompletion = ChatCompletion.builder()
                .id("chatcmpl-test-123")
                .created(System.currentTimeMillis() / 1000)
                .model("gpt-4o-mini")
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0)
                        .message(ChatCompletionMessage.builder()
                                .content("Patient exhibits sinus tachycardia.")
                                .refusal((String) null)
                                .build())
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .logprobs(Optional.empty())
                        .build())
                .usage(CompletionUsage.builder()
                        .promptTokens(18)
                        .completionTokens(8)
                        .totalTokens(26)
                        .build())
                .systemFingerprint("fp_test_abc")
                .build();

        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(mockCompletion);

        AiRequest request = AiRequest.builder()
                .model("gpt-4o-mini")
                .systemPrompt("You are an AI clinical assistant.")
                .messages(List.of(
                        AiMessage.user("Evaluate vitals: HR 120 bpm."),
                        AiMessage.assistant("Noted tachycardia."),
                        AiMessage.user("Any immediate precautions?")
                ))
                .temperature(0.5)
                .topP(0.9)
                .maxTokens(500)
                .build();

        AiResponse response = provider.generate(request);

        assertThat(response).isNotNull();
        assertThat(response.provider()).isEqualTo("OPENAI");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.content()).isEqualTo("Patient exhibits sinus tachycardia.");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.promptTokens()).isEqualTo(18);
        assertThat(response.completionTokens()).isEqualTo(8);
        assertThat(response.totalTokens()).isEqualTo(26);
        assertThat(response.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(response.metadata()).containsEntry("systemFingerprint", "fp_test_abc");

        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(completionService).create(captor.capture());
        ChatCompletionCreateParams params = captor.getValue();
        assertThat(params.model().asString()).isEqualTo("gpt-4o-mini");
        assertThat(params.temperature().orElse(null)).isEqualTo(0.5);
        assertThat(params.topP().orElse(null)).isEqualTo(0.9);
        assertThat(params.maxCompletionTokens().orElse(null)).isEqualTo(500L);
    }

    @Test
    @DisplayName("3. Streaming generates progressive chunks and completes with final AiResponse")
    void testStream_success() {
        List<ChatCompletionChunk> mockChunks = List.of(
                ChatCompletionChunk.builder()
                        .id("chunk-1")
                        .created(1L)
                        .model("gpt-4o-mini")
                        .addChoice(ChatCompletionChunk.Choice.builder()
                                .index(0)
                                .delta(ChatCompletionChunk.Choice.Delta.builder()
                                        .content("Cardiac ")
                                        .build())
                                .finishReason(java.util.Optional.empty())
                                .build())
                        .build(),
                ChatCompletionChunk.builder()
                        .id("chunk-2")
                        .created(2L)
                        .model("gpt-4o-mini")
                        .addChoice(ChatCompletionChunk.Choice.builder()
                                .index(0)
                                .delta(ChatCompletionChunk.Choice.Delta.builder()
                                        .content("rhythm is normal.")
                                        .build())
                                .finishReason(ChatCompletionChunk.Choice.FinishReason.STOP)
                                .build())
                        .usage(CompletionUsage.builder()
                                .promptTokens(12)
                                .completionTokens(6)
                                .totalTokens(18)
                                .build())
                        .build()
        );

        StreamResponse<ChatCompletionChunk> mockStreamResponse = new StreamResponse<>() {
            @Override
            public Stream<ChatCompletionChunk> stream() {
                return mockChunks.stream();
            }

            @Override
            public void close() {
            }
        };

        when(completionService.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(mockStreamResponse);

        AiRequest request = AiRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(AiMessage.user("Interpret rhythm strip")))
                .build();

        List<AiStreamChunk> receivedChunks = new ArrayList<>();
        AtomicReference<AiResponse> completeResponse = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean(false);

        AiStreamHandle handle = provider.stream(request, new AiStreamListener() {
            @Override
            public void onStart() {
                started.set(true);
            }

            @Override
            public void onChunk(AiStreamChunk chunk) {
                receivedChunks.add(chunk);
            }

            @Override
            public void onComplete(AiResponse response) {
                completeResponse.set(response);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        assertThat(handle).isNotNull();
        assertThat(started.get()).isTrue();
        assertThat(receivedChunks).hasSize(2);
        assertThat(receivedChunks.get(0).content()).isEqualTo("Cardiac ");
        assertThat(receivedChunks.get(0).chunkIndex()).isEqualTo(1);
        assertThat(receivedChunks.get(0).isLast()).isFalse();

        assertThat(receivedChunks.get(1).content()).isEqualTo("rhythm is normal.");
        assertThat(receivedChunks.get(1).chunkIndex()).isEqualTo(2);
        assertThat(receivedChunks.get(1).isLast()).isTrue();

        assertThat(completeResponse.get()).isNotNull();
        assertThat(completeResponse.get().content()).isEqualTo("Cardiac rhythm is normal.");
        assertThat(completeResponse.get().provider()).isEqualTo("OPENAI");
        assertThat(completeResponse.get().promptTokens()).isEqualTo(12);
        assertThat(completeResponse.get().completionTokens()).isEqualTo(6);
        assertThat(completeResponse.get().totalTokens()).isEqualTo(18);
    }

    @Test
    @DisplayName("4. Stream cancellation immediately stops chunk processing and closes connection")
    void testStream_cancellation() {
        AtomicBoolean closed = new AtomicBoolean(false);
        List<ChatCompletionChunk> mockChunks = List.of(
                ChatCompletionChunk.builder()
                        .id("chunk-1")
                        .created(1L)
                        .model("gpt-4o-mini")
                        .addChoice(ChatCompletionChunk.Choice.builder()
                                .index(0)
                                .delta(ChatCompletionChunk.Choice.Delta.builder().content("Part 1 ").build())
                                .finishReason(java.util.Optional.empty())
                                .build())
                        .build(),
                ChatCompletionChunk.builder()
                        .id("chunk-2")
                        .created(2L)
                        .model("gpt-4o-mini")
                        .addChoice(ChatCompletionChunk.Choice.builder()
                                .index(0)
                                .delta(ChatCompletionChunk.Choice.Delta.builder().content("Part 2 ").build())
                                .finishReason(java.util.Optional.empty())
                                .build())
                        .build()
        );

        StreamResponse<ChatCompletionChunk> mockStreamResponse = new StreamResponse<>() {
            @Override
            public Stream<ChatCompletionChunk> stream() {
                return mockChunks.stream();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        when(completionService.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(mockStreamResponse);

        AiRequest request = AiRequest.builder()
                .messages(List.of(AiMessage.user("Long stream query")))
                .build();

        List<AiStreamChunk> receivedChunks = new ArrayList<>();
        AtomicReference<AiStreamHandle> handleRef = new AtomicReference<>();

        provider.stream(request, new AiStreamListener() {
            @Override
            public void onHandle(AiStreamHandle handle) {
                handleRef.set(handle);
            }

            @Override
            public void onStart() {}

            @Override
            public void onChunk(AiStreamChunk chunk) {
                receivedChunks.add(chunk);
                // Cancel after first chunk
                handleRef.get().cancel();
            }

            @Override
            public void onComplete(AiResponse response) {}

            @Override
            public void onError(Throwable throwable) {}
        });

        assertThat(handleRef.get()).isNotNull();
        assertThat(handleRef.get().isCancelled()).isTrue();
        assertThat(closed.get()).isTrue();
        assertThat(receivedChunks).hasSize(1);
    }

    @Test
    @DisplayName("5. Maps 401 Unauthorized to AiProviderAuthenticationException")
    void testError_unauthorizedMapsToAuthenticationException() {
        UnauthorizedException uae = new UnauthorizedException(Headers.builder().build(), "Incorrect API key", com.openai.errors.OpenAIError.builder().build());
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(uae);

        AiRequest request = AiRequest.builder().messages(List.of(AiMessage.user("Hi"))).build();

        assertThatThrownBy(() -> provider.generate(request))
                .isInstanceOf(AiProviderAuthenticationException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    @DisplayName("6. Maps 403 Forbidden to AiProviderAuthenticationException")
    void testError_permissionDeniedMapsToAuthenticationException() {
        PermissionDeniedException pde = new PermissionDeniedException(Headers.builder().build(), "Access denied", com.openai.errors.OpenAIError.builder().build());
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(pde);

        AiRequest request = AiRequest.builder().messages(List.of(AiMessage.user("Hi"))).build();

        assertThatThrownBy(() -> provider.generate(request))
                .isInstanceOf(AiProviderAuthenticationException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    @DisplayName("7. Maps 429 RateLimit to AiProviderRateLimitException")
    void testError_rateLimitMapsToRateLimitException() {
        RateLimitException rle = new RateLimitException(Headers.builder().build(), "Rate limit reached", com.openai.errors.OpenAIError.builder().build());
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(rle);

        AiRequest request = AiRequest.builder().messages(List.of(AiMessage.user("Hi"))).build();

        assertThatThrownBy(() -> provider.generate(request))
                .isInstanceOf(AiProviderRateLimitException.class)
                .hasMessageContaining("HTTP 429");
    }

    @Test
    @DisplayName("8. Maps SocketTimeoutException in OpenAIIoException to AiProviderTimeoutException")
    void testError_timeoutMapsToTimeoutException() {
        OpenAIIoException oie = new OpenAIIoException("Read timed out", new SocketTimeoutException("connect timeout"));
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(oie);

        AiRequest request = AiRequest.builder().messages(List.of(AiMessage.user("Hi"))).build();

        assertThatThrownBy(() -> provider.generate(request))
                .isInstanceOf(AiProviderTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("9. Maps 500 InternalServerException to AiProviderUnavailableException")
    void testError_serverErrorMapsToUnavailableException() {
        InternalServerException ise = new InternalServerException(500, Headers.builder().build(), "OpenAI server error", com.openai.errors.OpenAIError.builder().build());
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(ise);

        AiRequest request = AiRequest.builder().messages(List.of(AiMessage.user("Hi"))).build();

        assertThatThrownBy(() -> provider.generate(request))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("10. isAvailable returns true on success, false on error")
    void testIsAvailable() {
        when(modelService.list()).thenReturn(null);
        assertThat(provider.isAvailable()).isTrue();

        when(modelService.list()).thenThrow(new RuntimeException("Network down"));
        assertThat(provider.isAvailable()).isFalse();
    }
}
