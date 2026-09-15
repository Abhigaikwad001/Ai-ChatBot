package com.chatbot.platform.security.util;

import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.infrastructure.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces ownership boundaries across entities so that User A can never access
 * or mutate User B's conversations, messages, or AI assets.
 */
@Component
public class OwnershipValidator {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public OwnershipValidator(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Asserts that the authenticated user matches the owner ID.
     */
    public void verifyOwnership(UUID resourceOwnerId, UUID authenticatedUserId) {
        if (resourceOwnerId == null || !resourceOwnerId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to access this resource");
        }
    }

    /**
     * Validates conversation existence and asserts that it is strictly owned by the authenticated user.
     * Throws 403 AccessDeniedException if the resource exists but belongs to a different user.
     */
    public Conversation verifyAndGetConversation(UUID conversationId, UUID authenticatedUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETED) {
            throw new ResourceNotFoundException("Conversation has been deleted");
        }

        return conversationRepository.findByIdWithMessages(conversationId, authenticatedUserId)
            .orElse(conversation);
    }

    /**
     * Validates message existence and asserts that its parent conversation is owned by the authenticated user.
     * Throws 403 AccessDeniedException if the message exists but belongs to another user's conversation.
     */
    public Message verifyAndGetMessage(UUID messageId, UUID authenticatedUserId) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + messageId));

        if (!message.getConversation().getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to access this message");
        }

        return message;
    }
}
