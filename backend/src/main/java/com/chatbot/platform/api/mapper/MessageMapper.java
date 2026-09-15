package com.chatbot.platform.api.mapper;

import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.core.domain.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        return new MessageResponse(
            message.getId(),
            message.getConversation() != null ? message.getConversation().getId() : null,
            message.getSequenceNumber(),
            message.getRole(),
            message.getContent(),
            message.getStatus(),
            message.getPromptTokens(),
            message.getCompletionTokens(),
            message.getMetadata(),
            message.getCreatedAt()
        );
    }
}
