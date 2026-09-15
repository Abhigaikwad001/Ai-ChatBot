package com.chatbot.platform.infrastructure.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.service.ai.AiProvider;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.core.service.ai.stream.AiStreamChunk;
import com.chatbot.platform.core.service.ai.stream.AiStreamHandle;
import com.chatbot.platform.core.service.ai.stream.AiStreamListener;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory test stub implementation of AiProvider for automated integration and slice tests.
 * Eliminates dependencies on external network services and permits controlled failure injection.
 */
@Component
@Profile("test")
@Primary
public class TestStubAiProvider implements AiProvider {

    private boolean failWithTimeout = false;
    private boolean failWithUnavailable = false;
    private boolean failDuringStream = false;
    private String customResponse = null;
    private List<String> customChunks = null;
    private AiRequest lastReceivedRequest = null;
    private final AtomicBoolean streamCancelled = new AtomicBoolean(false);

    public void setFailWithTimeout(boolean failWithTimeout) {
        this.failWithTimeout = failWithTimeout;
    }

    public void setFailWithUnavailable(boolean failWithUnavailable) {
        this.failWithUnavailable = failWithUnavailable;
    }

    public void setFailDuringStream(boolean failDuringStream) {
        this.failDuringStream = failDuringStream;
    }

    public void setCustomResponse(String customResponse) {
        this.customResponse = customResponse;
    }

    public void setCustomChunks(List<String> customChunks) {
        this.customChunks = customChunks;
    }

    public AiRequest getLastReceivedRequest() {
        return lastReceivedRequest;
    }

    public boolean isStreamCancelled() {
        return streamCancelled.get();
    }

    public void reset() {
        this.failWithTimeout = false;
        this.failWithUnavailable = false;
        this.failDuringStream = false;
        this.customResponse = null;
        this.customChunks = null;
        this.lastReceivedRequest = null;
        this.streamCancelled.set(false);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OLLAMA;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        this.lastReceivedRequest = request;
        if (failWithTimeout) {
            throw new AiProviderTimeoutException("OLLAMA", "Simulated test timeout after 5000ms");
        }
        if (failWithUnavailable) {
            throw new AiProviderUnavailableException("OLLAMA", "Simulated test service offline");
        }

        String lastContent = request.messages().isEmpty()
            ? ""
            : request.messages().get(request.messages().size() - 1).content();

        String responseText = (customResponse != null)
            ? customResponse
            : "AI clinical assistant analysis for: " + lastContent;

        return AiResponse.builder()
            .content(responseText)
            .provider("OLLAMA")
            .model(request.model())
            .promptTokens(14)
            .completionTokens(32)
            .totalTokens(46)
            .finishReason("stop")
            .latencyMs(30L)
            .metadata(Map.of("simulated", true))
            .build();
    }

    @Override
    public AiStreamHandle stream(AiRequest request, AiStreamListener listener) {
        this.lastReceivedRequest = request;
        this.streamCancelled.set(false);

        if (failWithTimeout) {
            listener.onError(new AiProviderTimeoutException("OLLAMA", "Simulated test timeout after 5000ms"));
            return new AiStreamHandle() {
                @Override public void cancel() { streamCancelled.set(true); }
                @Override public boolean isCancelled() { return streamCancelled.get(); }
            };
        }

        if (failWithUnavailable) {
            listener.onError(new AiProviderUnavailableException("OLLAMA", "Simulated test service offline"));
            return new AiStreamHandle() {
                @Override public void cancel() { streamCancelled.set(true); }
                @Override public boolean isCancelled() { return streamCancelled.get(); }
            };
        }

        listener.onStart();

        if (failDuringStream) {
            listener.onChunk(new AiStreamChunk("Partial response before failure...", request.model(), 1, false));
            listener.onError(new AiProviderException("OLLAMA", "Simulated stream interruption mid-transfer", null));
            return new AiStreamHandle() {
                @Override public void cancel() { streamCancelled.set(true); }
                @Override public boolean isCancelled() { return streamCancelled.get(); }
            };
        }

        List<String> chunks = (customChunks != null) ? customChunks :
            (customResponse != null)
                ? Arrays.asList(customResponse.split("(?<=\\s)"))
                : List.of("AI ", "clinical ", "analysis.");

        StringBuilder accumulated = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (streamCancelled.get()) {
                break;
            }
            String chunkText = chunks.get(i);
            accumulated.append(chunkText);
            boolean isLast = (i == chunks.size() - 1);
            listener.onChunk(new AiStreamChunk(chunkText, request.model(), i + 1, isLast));
        }

        if (!streamCancelled.get()) {
            AiResponse completeResponse = AiResponse.builder()
                .content(accumulated.toString())
                .provider("OLLAMA")
                .model(request.model())
                .promptTokens(14)
                .completionTokens(32)
                .totalTokens(46)
                .finishReason("stop")
                .latencyMs(25L)
                .metadata(Map.of("simulated", true))
                .build();

            listener.onComplete(completeResponse);
        }

        return new AiStreamHandle() {
            @Override public void cancel() { streamCancelled.set(true); }
            @Override public boolean isCancelled() { return streamCancelled.get(); }
        };
    }

    @Override
    public boolean isAvailable() {
        return !failWithUnavailable;
    }
}
