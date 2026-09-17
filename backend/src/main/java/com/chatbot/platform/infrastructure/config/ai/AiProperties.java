package com.chatbot.platform.infrastructure.config.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized AI Provider and Model configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiProperties {

    private ProviderType defaultProvider = ProviderType.OPENAI;
    private String defaultModel = "gpt-4o-mini";
    private Double defaultTemperature = 0.7;
    private Integer defaultMaxTokens = 2048;
    private Double defaultTopP = 1.0;
    private Integer timeoutSeconds = 60;
    private Integer maxRetries = 2;
    private Long retryBackoffMs = 1000L;
    private OpenAiProperties openai = new OpenAiProperties();
    private OllamaProperties ollama = new OllamaProperties();
    private ContextProperties context = new ContextProperties();
    private StreamingProperties streaming = new StreamingProperties();

    @Getter
    @Setter
    public static class OpenAiProperties {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private Integer connectTimeoutMs = 10000;
        private Integer readTimeoutMs = 60000;
    }

    @Getter
    @Setter
    public static class OllamaProperties {
        private String baseUrl = "http://localhost:11434";
        private String apiKey = "";
        private Integer connectTimeoutMs = 5000;
        private Integer readTimeoutMs = 60000;
    }

    @Getter
    @Setter
    public static class ContextProperties {
        private Integer maxMessages = 20;
        private Integer maxTokens = 4096;
        private Integer reservedOutputTokens = 1024;
    }

    @Getter
    @Setter
    public static class StreamingProperties {
        private boolean enabled = true;
        private Long timeoutMs = 120000L;
        private boolean heartbeatEnabled = true;
        private Long heartbeatIntervalMs = 15000L;

        public boolean isHeartbeatEnabled() {
            return heartbeatEnabled;
        }

        public boolean getHeartbeatEnabled() {
            return heartbeatEnabled;
        }
    }
}
