package com.chatbot.platform.api.mapper;

import com.chatbot.platform.api.dto.ai.AiModelConfigDto;
import com.chatbot.platform.api.dto.conversation.ConversationResponse;
import com.chatbot.platform.api.dto.conversation.ConversationSummaryResponse;
import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ConversationMapper {

    private final MessageMapper messageMapper;

    public ConversationMapper(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public ConversationSummaryResponse toSummaryResponse(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        int count = 0;
        if (org.hibernate.Hibernate.isInitialized(conversation.getMessages()) && conversation.getMessages() != null) {
            count = conversation.getMessages().size();
        }
        return toSummaryResponse(conversation, count);
    }

    public ConversationSummaryResponse toSummaryResponse(Conversation conversation, int messageCount) {
        if (conversation == null) {
            return null;
        }
        return new ConversationSummaryResponse(
            conversation.getId(),
            conversation.getUser() != null ? conversation.getUser().getId() : null,
            conversation.getTitle(),
            toAiModelConfigDto(conversation.getAiModelConfig()),
            conversation.getStatus(),
            conversation.getMetadata(),
            messageCount,
            conversation.getCreatedAt(),
            conversation.getUpdatedAt()
        );
    }

    public ConversationResponse toResponse(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        List<MessageResponse> messageResponses = (conversation.getMessages() != null)
            ? conversation.getMessages().stream().map(messageMapper::toResponse).toList()
            : Collections.emptyList();

        return new ConversationResponse(
            conversation.getId(),
            conversation.getUser() != null ? conversation.getUser().getId() : null,
            conversation.getTitle(),
            conversation.getSystemPrompt(),
            toAiModelConfigDto(conversation.getAiModelConfig()),
            conversation.getStatus(),
            conversation.getMetadata(),
            messageResponses,
            conversation.getCreatedAt(),
            conversation.getUpdatedAt()
        );
    }

    public AiModelConfig toAiModelConfig(AiModelConfigDto dto) {
        if (dto == null) {
            return AiModelConfig.defaultConfig();
        }
        return new AiModelConfig(
            dto.provider(),
            dto.modelName(),
            dto.temperature() != null ? dto.temperature() : 0.7,
            dto.maxTokens() != null ? dto.maxTokens() : 2048,
            dto.topP() != null ? dto.topP() : 1.0
        );
    }

    public AiModelConfigDto toAiModelConfigDto(AiModelConfig config) {
        if (config == null) {
            return null;
        }
        return new AiModelConfigDto(
            config.getProvider(),
            config.getModelName(),
            config.getTemperature(),
            config.getMaxTokens(),
            config.getTopP()
        );
    }
}
