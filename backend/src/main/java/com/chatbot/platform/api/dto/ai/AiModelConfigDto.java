package com.chatbot.platform.api.dto.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiModelConfigDto(
    @NotNull(message = "Provider is required")
    ProviderType provider,

    @NotBlank(message = "Model name cannot be blank")
    String modelName,

    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Temperature cannot exceed 2.0")
    Double temperature,

    @Min(value = 1, message = "Max tokens must be at least 1")
    @Max(value = 131072, message = "Max tokens cannot exceed 131,072")
    Integer maxTokens,

    @DecimalMin(value = "0.0", message = "Top-p must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Top-p cannot exceed 1.0")
    Double topP
) {}
