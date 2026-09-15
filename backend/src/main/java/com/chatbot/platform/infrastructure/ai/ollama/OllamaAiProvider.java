package com.chatbot.platform.infrastructure.ai.ollama;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiProvider;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.core.service.ai.AiRole;
import com.chatbot.platform.infrastructure.ai.ollama.dto.OllamaChatRequest;
import com.chatbot.platform.infrastructure.ai.ollama.dto.OllamaChatResponse;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import com.chatbot.platform.core.service.ai.stream.AiStreamChunk;
import com.chatbot.platform.core.service.ai.stream.AiStreamHandle;
import com.chatbot.platform.core.service.ai.stream.AiStreamListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-grade Ollama implementation of the AiProvider abstraction.
 * Supports both local Ollama daemons and remote/cloud Ollama endpoints via
 * configurable
 * base URL, connect/read timeouts, and optional Bearer token authentication.
 */
@Component
public class OllamaAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiProvider.class);
    private static final String PROVIDER_NAME = "OLLAMA";

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaAiProvider(
            AiProperties aiProperties,
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();

        AiProperties.OllamaProperties ollamaProps = aiProperties.getOllama();
        Duration connectTimeout = Duration.ofMillis(ollamaProps.getConnectTimeoutMs());
        Duration readTimeout = Duration.ofMillis(ollamaProps.getReadTimeoutMs());

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder builder = restClientBuilder
                .baseUrl(ollamaProps.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (ollamaProps.getApiKey() != null && !ollamaProps.getApiKey().trim().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ollamaProps.getApiKey().trim());
            log.info("Configured Ollama client with externalized Bearer credential header");
        }

        this.restClient = builder.build();
        log.info("Initialized OllamaAiProvider against base URL: [{}] with timeouts (connect: {}ms, read: {}ms)",
                ollamaProps.getBaseUrl(), ollamaProps.getConnectTimeoutMs(), ollamaProps.getReadTimeoutMs());
    }

    /**
     * Package-private constructor for unit and mock testing.
     */
    OllamaAiProvider(AiProperties aiProperties, RestClient restClient, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.restClient = restClient;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    OllamaAiProvider(AiProperties aiProperties, RestClient restClient) {
        this(aiProperties, restClient, new ObjectMapper());
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OLLAMA;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AiRequest cannot be null");
        }

        OllamaChatRequest payload = mapToOllamaRequest(request);
        long startTime = System.currentTimeMillis();
        int maxRetries = Math.max(0, aiProperties.getMaxRetries());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Dispatching completion request to Ollama (attempt {}/{}) for model: [{}]",
                        attempt + 1, maxRetries + 1, payload.model());

                OllamaChatResponse response = restClient.post()
                        .uri("/api/chat")
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            int code = res.getStatusCode().value();
                            if (code == 401 || code == 403) {
                                throw new AiProviderAuthenticationException(PROVIDER_NAME,
                                        "Authentication failed with Ollama provider (HTTP " + code + ")");
                            }
                            if (code == 429) {
                                throw new AiProviderRateLimitException(PROVIDER_NAME,
                                        "Rate limit exceeded for Ollama provider");
                            }
                            throw new AiProviderException(PROVIDER_NAME,
                                    "Client request error from Ollama provider: HTTP " + code);
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                            throw new AiProviderUnavailableException(PROVIDER_NAME,
                                    "Ollama provider server error: HTTP " + res.getStatusCode().value());
                        })
                        .body(OllamaChatResponse.class);

                long latencyMs = System.currentTimeMillis() - startTime;
                return mapToAiResponse(response, latencyMs, payload.model());

            } catch (AiProviderAuthenticationException | AiProviderRateLimitException ex) {
                // Non-retryable client security / quota errors
                throw ex;
            } catch (ResourceAccessException ex) {
                boolean isTimeout = ex.getCause() instanceof java.net.SocketTimeoutException
                        || (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("timeout"));

                if (isTimeout) {
                    log.warn("Ollama request timed out after attempt {}/{}", attempt + 1, maxRetries + 1);
                    if (attempt == maxRetries) {
                        throw new AiProviderTimeoutException(PROVIDER_NAME,
                                "Ollama completion timed out after " + aiProperties.getOllama().getReadTimeoutMs()
                                        + "ms",
                                ex);
                    }
                } else {
                    log.warn("I/O error communicating with Ollama provider (attempt {}/{}): {}",
                            attempt + 1, maxRetries + 1, ex.getMessage());
                    if (attempt == maxRetries) {
                        throw new AiProviderUnavailableException(PROVIDER_NAME,
                                "Cannot connect to Ollama service. Service may be offline.", ex);
                    }
                }
                backoff(attempt);

            } catch (AiProviderUnavailableException ex) {
                log.warn("Transient 5xx received from Ollama provider (attempt {}/{}): {}",
                        attempt + 1, maxRetries + 1, ex.getMessage());
                if (attempt == maxRetries) {
                    throw ex;
                }
                backoff(attempt);

            } catch (RestClientResponseException ex) {
                throw new AiProviderException(PROVIDER_NAME,
                        "Provider returned error HTTP " + ex.getStatusCode().value(), ex);
            } catch (Exception ex) {
                if (ex instanceof AiProviderException ape) {
                    throw ape;
                }
                throw new AiProviderException(PROVIDER_NAME, "Unexpected error during Ollama generation", ex);
            }
        }

        throw new AiProviderUnavailableException(PROVIDER_NAME,
                "Failed to receive response from Ollama after all retries");
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.debug("Ollama availability check failed: {}", ex.getMessage());
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

        OllamaChatRequest payload = mapToOllamaRequest(request, true);
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
            log.info("OLLAMA STREAM REQUEST: model=[{}]", payload.model());

            restClient.post()
                    .uri("/api/chat")
                    .header(HttpHeaders.ACCEPT, "application/x-ndjson", MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((clientRequest, clientResponse) -> {
                        if (cancelled.get()) {
                            return null;
                        }

                        HttpStatusCode statusCode = clientResponse.getStatusCode();

                        if (statusCode.is4xxClientError()) {
                            int code = statusCode.value();
                            if (code == 401 || code == 403) {
                                throw new AiProviderAuthenticationException(PROVIDER_NAME,
                                        "Authentication failed with Ollama provider (HTTP " + code + ")");
                            }
                            if (code == 429) {
                                throw new AiProviderRateLimitException(PROVIDER_NAME,
                                        "Rate limit exceeded for Ollama provider");
                            }
                            throw new AiProviderException(PROVIDER_NAME,
                                    "Client request error from Ollama provider: HTTP " + code);
                        }

                        if (statusCode.is5xxServerError()) {
                            throw new AiProviderUnavailableException(PROVIDER_NAME,
                                    "Ollama provider server error: HTTP " + statusCode.value());
                        }

                        try (InputStream is = clientResponse.getBody();
                                BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(is, StandardCharsets.UTF_8))) {

                            streamCloseable.set(is);
                            listener.onStart();
                            String line;
                            StringBuilder fullContent = new StringBuilder();
                            String finalModel = payload.model();
                            Integer promptEvalCount = null;
                            Integer evalCount = null;
                            String doneReason = null;
                            Long totalDuration = null;
                            int chunkIndex = 1;

                            while (!cancelled.get() && (line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }

                                OllamaChatResponse chunkResponse;
                                try {
                                    chunkResponse = objectMapper.readValue(line, OllamaChatResponse.class);
                                } catch (Exception ex) {
                                    log.error("Malformed NDJSON line received from Ollama stream: {}", line);
                                    throw new AiProviderException(PROVIDER_NAME,
                                            "Malformed NDJSON line received from Ollama stream", ex);
                                }

                                if (chunkResponse.message() != null && chunkResponse.message().content() != null
                                        && !chunkResponse.message().content().isEmpty()) {
                                    String delta = chunkResponse.message().content();
                                    fullContent.append(delta);
                                    boolean isLast = Boolean.TRUE.equals(chunkResponse.done());
                                    listener.onChunk(new AiStreamChunk(delta,
                                            chunkResponse.model() != null ? chunkResponse.model() : finalModel,
                                            chunkIndex++, isLast));
                                }

                                if (Boolean.TRUE.equals(chunkResponse.done())) {
                                    if (chunkResponse.model() != null) {
                                        finalModel = chunkResponse.model();
                                    }
                                    promptEvalCount = chunkResponse.promptEvalCount();
                                    evalCount = chunkResponse.evalCount();
                                    doneReason = chunkResponse.doneReason();
                                    totalDuration = chunkResponse.totalDuration();
                                    break;
                                }
                            }

                            if (!cancelled.get()) {
                                long latencyMs = System.currentTimeMillis() - startTime;
                                Map<String, Object> metadata = new HashMap<>();
                                if (totalDuration != null) {
                                    metadata.put("totalDurationNanos", totalDuration);
                                }
                                metadata.put("latencyMs", latencyMs);

                                AiResponse completeResponse = AiResponse.builder()
                                        .content(fullContent.toString())
                                        .provider(PROVIDER_NAME)
                                        .model(finalModel)
                                        .promptTokens(promptEvalCount)
                                        .completionTokens(evalCount)
                                        .finishReason(doneReason != null ? doneReason : "stop")
                                        .latencyMs(latencyMs)
                                        .metadata(metadata)
                                        .build();

                                listener.onComplete(completeResponse);
                            } else {
                                log.info("Ollama stream reading stopped due to client cancellation");
                            }
                        } finally {
                            streamCloseable.set(null);
                        }
                        return null;
                    });

        } catch (Throwable ex) {
            if (!cancelled.get()) {
                Throwable translated = translateStreamingException(ex);
                listener.onError(translated);
            }
        }

        return handle;
    }

    private Throwable translateStreamingException(Throwable ex) {
        if (ex instanceof AiProviderException) {
            return ex;
        }
        if (ex instanceof ResourceAccessException rae) {
            boolean isTimeout = rae.getCause() instanceof java.net.SocketTimeoutException
                    || (rae.getMessage() != null && rae.getMessage().toLowerCase(Locale.ROOT).contains("timeout"));
            if (isTimeout) {
                return new AiProviderTimeoutException(PROVIDER_NAME, "Ollama streaming timed out", rae);
            }
            return new AiProviderUnavailableException(PROVIDER_NAME, "Cannot connect to Ollama streaming service", rae);
        }
        if (ex instanceof RestClientResponseException rcre) {
            return new AiProviderException(PROVIDER_NAME, "Provider returned HTTP " + rcre.getStatusCode().value(),
                    rcre);
        }
        return new AiProviderException(PROVIDER_NAME, "Unexpected error during Ollama streaming", ex);
    }

    private OllamaChatRequest mapToOllamaRequest(AiRequest request) {
        return mapToOllamaRequest(request, false);
    }

    private OllamaChatRequest mapToOllamaRequest(AiRequest request, boolean stream) {
        List<OllamaChatRequest.OllamaMessageDto> ollamaMessages = new ArrayList<>();

        // 1. System instruction turn
        if (request.systemPrompt() != null && !request.systemPrompt().trim().isBlank()) {
            ollamaMessages.add(new OllamaChatRequest.OllamaMessageDto(
                    "system",
                    request.systemPrompt().trim()));
        }

        // 2. Message history turns
        for (AiMessage msg : request.messages()) {
            String roleStr = switch (msg.role()) {
                case SYSTEM -> "system";
                case USER -> "user";
                case ASSISTANT -> "assistant";
            };
            ollamaMessages.add(new OllamaChatRequest.OllamaMessageDto(roleStr, msg.content()));
        }

        // 3. Execution options
        Map<String, Object> options = new HashMap<>();
        if (request.temperature() != null) {
            options.put("temperature", request.temperature());
        }
        if (request.topP() != null) {
            options.put("top_p", request.topP());
        }
        if (request.maxTokens() != null) {
            options.put("num_predict", request.maxTokens());
        }
        if (request.options() != null) {
            options.putAll(request.options());
        }

        String modelName = (request.model() != null && !request.model().isBlank())
                ? request.model()
                : aiProperties.getDefaultModel();

        return new OllamaChatRequest(modelName, ollamaMessages, stream, options);
    }

    private AiResponse mapToAiResponse(OllamaChatResponse response, long latencyMs, String requestedModel) {
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new AiProviderException(PROVIDER_NAME, "Ollama returned an empty or malformed completion payload");
        }

        String content = response.message().content();
        String model = (response.model() != null) ? response.model() : requestedModel;
        Integer promptTokens = response.promptEvalCount();
        Integer completionTokens = response.evalCount();
        String finishReason = (response.doneReason() != null) ? response.doneReason() : "stop";

        Map<String, Object> metadata = new HashMap<>();
        if (response.totalDuration() != null) {
            metadata.put("totalDurationNanos", response.totalDuration());
        }
        metadata.put("latencyMs", latencyMs);

        return AiResponse.builder()
                .content(content)
                .provider(PROVIDER_NAME)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .finishReason(finishReason)
                .latencyMs(latencyMs)
                .metadata(metadata)
                .build();
    }

    private void backoff(int attempt) {
        try {
            long backoff = aiProperties.getRetryBackoffMs() * (long) Math.pow(2, attempt);
            Thread.sleep(Math.min(backoff, 5000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
