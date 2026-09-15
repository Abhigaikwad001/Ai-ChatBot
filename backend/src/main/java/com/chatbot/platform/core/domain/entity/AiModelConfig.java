package com.chatbot.platform.core.domain.entity;

import com.chatbot.platform.core.domain.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Embeddable AI Provider and Model configuration parameters.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfig implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private ProviderType provider = ProviderType.OLLAMA;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName = "llama3.2:3b";

    @Column(name = "temperature", nullable = false)
    private Double temperature = 0.7;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 2048;

    @Column(name = "top_p", nullable = false)
    private Double topP = 1.0;

    public static AiModelConfig defaultConfig() {
        return new AiModelConfig(ProviderType.OLLAMA, "llama3.2:3b", 0.7, 2048, 1.0);
    }
}
