package com.chatbot.platform.infrastructure.ai.openai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiProvider;
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
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.core.http.StreamResponse;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionAssistantMessageParam;
import com.openai.models.ChatCompletionChunk;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionStreamOptions;
import com.openai.models.CompletionUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-grade OpenAI implementation of the AiProvider abstraction.
 * Communicates with the official OpenAI Chat Completions API via the official OpenAI Java SDK.
 * Supports synchronous generation and reactive SSE streaming with cancellation and token usage tracking.
 */
@Component
public class OpenAiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAiProvider.class);
    private static final String PROVIDER_NAME = "OPENAI";

    private final OpenAIClient client;
    private final AiProperties aiProperties;

    @Autowired
    public OpenAiAiProvider(AiProperties aiProperties) {
        this.aiProperties = aiProperties;

        AiProperties.OpenAiProperties openAiProps = (aiProperties.getOpenai() != null)
                ? aiProperties.getOpenai()
                : new AiProperties.OpenAiProperties();

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        // 1. Externalized API key resolution: config property takes precedence, then environment variable
        String apiKey = openAiProps.getApiKey();
        if (apiKey == null || apiKey.trim().isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey != null && !apiKey.trim().isBlank()) {
            builder.apiKey(apiKey.trim());
            log.info("Initialized OpenAI client with externalized API credentials");
        } else {
            // Provide non-empty placeholder so client initializes safely without crashing during startup or offline test runs
            builder.apiKey("missing-api-key");
            log.warn("OPENAI_API_KEY is not configured; calls to OpenAI API will fail until configured");
        }

        // 2. Base URL
        if (openAiProps.getBaseUrl() != null && !openAiProps.getBaseUrl().trim().isBlank()) {
            builder.baseUrl(openAiProps.getBaseUrl().trim());
        }

        // 3. Timeouts
        int connectTimeoutMs = (openAiProps.getConnectTimeoutMs() != null) ? openAiProps.getConnectTimeoutMs() : 10000;
        int readTimeoutMs = (openAiProps.getReadTimeoutMs() != null) ? openAiProps.getReadTimeoutMs() : 60000;
        builder.timeout(Timeout.builder()
                .connect(Duration.ofMillis(connectTimeoutMs))
                .read(Duration.ofMillis(readTimeoutMs))
                .build());

        // 4. Retries
        int maxRetries = Math.max(0, aiProperties.getMaxRetries());
        builder.maxRetries(maxRetries);

        this.client = builder.build();
        log.info("Initialized OpenAiAiProvider against base URL: [{}] with timeouts (connect: {}ms, read: {}ms, retries: {})",
                openAiProps.getBaseUrl(), connectTimeoutMs, readTimeoutMs, maxRetries);
    }

    /**
     * Package-private constructor for unit and mock testing.
     */
    OpenAiAiProvider(AiProperties aiProperties, OpenAIClient client) {
        this.aiProperties = aiProperties;
        this.client = client;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AiRequest cannot be null");
        }

        ChatCompletionCreateParams params = mapToCreateParams(request, false);
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Dispatching chat completion request to OpenAI for model: [{}]", params.model().asString());
            ChatCompletion completion = client.chat().completions().create(params);
            long latencyMs = System.currentTimeMillis() - startTime;
            return mapToAiResponse(completion, latencyMs, params.model().asString());
        } catch (Throwable ex) {
            throw translateOpenAiException(ex);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            client.models().list();
            return true;
        } catch (Exception ex) {
            log.debug("OpenAI availability probe failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public AiStreamHandle stream(AiRequest request, AiStreamListener listener) {
        if (request == null) {
            throw new IllegalArgumentException("AiRequest cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("AiStreamListener cannot be null");
        }

        ChatCompletionCreateParams params = mapToCreateParams(request, true);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<AutoCloseable> streamCloseable = new AtomicReference<>(null);
        long startTime = System.currentTimeMillis();

        AiStreamHandle handle = new AiStreamHandle() {
            @Override
            public void cancel() {
                cancelled.set(true);
                AutoCloseable closeable = streamCloseable.get();
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        listener.onHandle(handle);

        try {
            log.info("OPENAI STREAM REQUEST: model=[{}]", params.model().asString());
            StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(params);
            streamCloseable.set(streamResponse);

            try (streamResponse) {
                listener.onStart();
                StringBuilder fullContent = new StringBuilder();
                String finalModel = params.model().asString();
                String finishReason = "stop";
                Integer promptTokens = null;
                Integer completionTokens = null;
                Integer totalTokens = null;
                int chunkIndex = 1;

                for (ChatCompletionChunk chunk : (Iterable<ChatCompletionChunk>) streamResponse.stream()::iterator) {
                    if (cancelled.get()) {
                        break;
                    }

                    if (chunk.model() != null) {
                        finalModel = chunk.model();
                    }

                    if (chunk.usage().isPresent()) {
                        CompletionUsage usage = chunk.usage().get();
                        promptTokens = (int) usage.promptTokens();
                        completionTokens = (int) usage.completionTokens();
                        totalTokens = (int) usage.totalTokens();
                    }

                    if (chunk.choices() != null) {
                        for (ChatCompletionChunk.Choice choice : chunk.choices()) {
                            if (choice.finishReason().isPresent()) {
                                finishReason = choice.finishReason().get().toString();
                            }
                            if (choice.delta() != null && choice.delta().content().isPresent()) {
                                String delta = choice.delta().content().get();
                                if (!delta.isEmpty()) {
                                    fullContent.append(delta);
                                    boolean isLast = choice.finishReason().isPresent();
                                    listener.onChunk(new AiStreamChunk(delta, finalModel, chunkIndex++, isLast));
                                }
                            }
                        }
                    }
                }

                if (!cancelled.get()) {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("latencyMs", latencyMs);

                    AiResponse completeResponse = AiResponse.builder()
                            .content(fullContent.toString())
                            .provider(PROVIDER_NAME)
                            .model(finalModel)
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .totalTokens(totalTokens)
                            .finishReason(finishReason)
                            .latencyMs(latencyMs)
                            .metadata(metadata)
                            .build();

                    listener.onComplete(completeResponse);
                } else {
                    log.info("OpenAI stream reading stopped due to client cancellation");
                }
            } finally {
                streamCloseable.set(null);
            }
        } catch (Throwable ex) {
            if (!cancelled.get()) {
                Throwable translated = translateOpenAiException(ex);
                listener.onError(translated);
            }
        }

        return handle;
    }

    private ChatCompletionCreateParams mapToCreateParams(AiRequest request, boolean stream) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();

        // 1. Model resolution
        String modelName = (request.model() != null && !request.model().isBlank())
                ? request.model()
                : aiProperties.getDefaultModel();
        builder.model(modelName);

        // 2. System instruction turn
        if (request.systemPrompt() != null && !request.systemPrompt().trim().isBlank()) {
            builder.addSystemMessage(request.systemPrompt().trim());
        }

        // 3. Dialogue history turns
        for (AiMessage msg : request.messages()) {
            switch (msg.role()) {
                case SYSTEM -> builder.addSystemMessage(msg.content());
                case USER -> builder.addUserMessage(msg.content());
                case ASSISTANT -> builder.addMessage(
                        ChatCompletionAssistantMessageParam.builder().content(msg.content()).build()
                );
            }
        }

        // 4. Generation hyperparameters
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        if (request.maxTokens() != null) {
            builder.maxCompletionTokens(request.maxTokens().longValue());
        }

        // 5. Streaming options (include token usage in streaming summary)
        if (stream) {
            builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
        }

        return builder.build();
    }

    private AiResponse mapToAiResponse(ChatCompletion completion, long latencyMs, String requestedModel) {
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new AiProviderException(PROVIDER_NAME, "OpenAI returned an empty completion payload");
        }

        ChatCompletion.Choice choice = completion.choices().get(0);
        String content = (choice.message() != null)
                ? choice.message().content().orElse("")
                : "";
        String model = (completion.model() != null) ? completion.model() : requestedModel;
        String finishReason = (choice.finishReason() != null) ? choice.finishReason().asString() : "stop";

        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;

        if (completion.usage().isPresent()) {
            CompletionUsage usage = completion.usage().get();
            promptTokens = (int) usage.promptTokens();
            completionTokens = (int) usage.completionTokens();
            totalTokens = (int) usage.totalTokens();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("latencyMs", latencyMs);
        completion.systemFingerprint().ifPresent(sf -> metadata.put("systemFingerprint", sf));

        return AiResponse.builder()
                .content(content)
                .provider(PROVIDER_NAME)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .finishReason(finishReason)
                .latencyMs(latencyMs)
                .metadata(metadata)
                .build();
    }

    private RuntimeException translateOpenAiException(Throwable ex) {
        if (ex instanceof RuntimeException re && re instanceof AiProviderException) {
            return re;
        }
        if (ex instanceof UnauthorizedException uae) {
            return new AiProviderAuthenticationException(PROVIDER_NAME,
                    "Authentication failed with OpenAI provider (HTTP 401)", uae);
        }
        if (ex instanceof PermissionDeniedException pde) {
            return new AiProviderAuthenticationException(PROVIDER_NAME,
                    "Permission denied by OpenAI provider (HTTP 403)", pde);
        }
        if (ex instanceof RateLimitException rle) {
            return new AiProviderRateLimitException(PROVIDER_NAME,
                    "Rate limit or quota exceeded for OpenAI provider (HTTP 429)", rle);
        }
        if (ex instanceof InternalServerException ise) {
            return new AiProviderUnavailableException(PROVIDER_NAME,
                    "OpenAI provider internal server error (HTTP 500)", ise);
        }
        if (ex instanceof OpenAIIoException oie) {
            boolean isTimeout = oie.getCause() instanceof java.net.SocketTimeoutException
                    || (oie.getMessage() != null && oie.getMessage().toLowerCase(Locale.ROOT).contains("timeout"));
            if (isTimeout) {
                return new AiProviderTimeoutException(PROVIDER_NAME, "OpenAI request timed out", oie);
            }
            return new AiProviderUnavailableException(PROVIDER_NAME,
                    "Cannot connect to OpenAI service. Network or DNS failure.", oie);
        }
        if (ex instanceof OpenAIServiceException ose) {
            int code = ose.statusCode();
            if (code >= 500) {
                return new AiProviderUnavailableException(PROVIDER_NAME,
                        "OpenAI provider server error: HTTP " + code, ose);
            }
            return new AiProviderException(PROVIDER_NAME,
                    "Client request error from OpenAI provider: HTTP " + code, ose);
        }
        if (ex instanceof OpenAIException oae) {
            return new AiProviderException(PROVIDER_NAME,
                    "OpenAI provider error: " + oae.getMessage(), oae);
        }
        return new AiProviderException(PROVIDER_NAME,
                "Unexpected error during OpenAI generation: " + ex.getMessage(), ex);
    }
}
