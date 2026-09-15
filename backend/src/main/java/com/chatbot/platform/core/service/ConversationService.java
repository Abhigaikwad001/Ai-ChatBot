package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.common.PageResponse;
import com.chatbot.platform.api.dto.conversation.ConversationResponse;
import com.chatbot.platform.api.dto.conversation.ConversationSummaryResponse;
import com.chatbot.platform.api.dto.conversation.CreateConversationRequest;
import com.chatbot.platform.api.dto.conversation.UpdateConversationRequest;
import com.chatbot.platform.api.mapper.ConversationMapper;
import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.exception.ConversationDeletedException;
import com.chatbot.platform.infrastructure.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service managing conversation lifecycles, creation, listing, updates, and soft deletions.
 * Enforces strict user ownership and transaction boundaries.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String DEFAULT_CONVERSATION_TITLE = "New conversation";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationMapper conversationMapper;

    public ConversationService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        UserRepository userRepository,
        ConversationMapper conversationMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.conversationMapper = conversationMapper;
    }

    /**
     * Creates a new conversation owned by the authenticated user.
     */
    @Transactional
    public ConversationResponse createConversation(UUID userId, CreateConversationRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String title = (request != null && request.title() != null && !request.title().isBlank())
            ? request.title().trim()
            : DEFAULT_CONVERSATION_TITLE;

        String systemPrompt = (request != null && request.systemPrompt() != null)
            ? request.systemPrompt().trim()
            : null;

        AiModelConfig aiModelConfig = (request != null && request.aiModelConfig() != null)
            ? conversationMapper.toAiModelConfig(request.aiModelConfig())
            : AiModelConfig.defaultConfig();

        Conversation conversation = new Conversation(user, title, systemPrompt, aiModelConfig);

        if (request != null && request.metadata() != null) {
            conversation.setMetadata(new HashMap<>(request.metadata()));
        }

        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversation [{}] created for user [{}]", saved.getId(), userId);

        return conversationMapper.toResponse(saved);
    }

    /**
     * Lists conversations owned strictly by the authenticated user, ordered by most recently active first.
     * Excludes soft-deleted conversations.
     */
    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> listConversations(UUID userId, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Conversation> conversationPage = conversationRepository.findByUserIdAndStatusNot(
            userId,
            ConversationStatus.DELETED,
            pageable
        );

        List<ConversationSummaryResponse> summaries = conversationPage.getContent().stream()
            .map(c -> {
                int count = (int) messageRepository.countByConversationId(c.getId());
                return conversationMapper.toSummaryResponse(c, count);
            })
            .toList();

        Page<ConversationSummaryResponse> summaryPage = new PageImpl<>(
            summaries,
            pageable,
            conversationPage.getTotalElements()
        );

        return PageResponse.from(summaryPage);
    }

    /**
     * Retrieves a single conversation by ID verifying ownership.
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.getUser().getId().equals(userId)) {
            log.warn("Unauthorized access attempt: user [{}] tried to read conversation [{}]", userId, conversationId);
            throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETED) {
            throw new ResourceNotFoundException("Conversation has been deleted");
        }

        Conversation fullConversation = conversationRepository.findByIdWithMessages(conversationId, userId)
            .orElse(conversation);

        return conversationMapper.toResponse(fullConversation);
    }

    /**
     * Updates conversation title and metadata. Only the owner can rename or update.
     */
    @Transactional
    public ConversationResponse updateConversation(UUID conversationId, UUID userId, UpdateConversationRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.getUser().getId().equals(userId)) {
            log.warn("Unauthorized rename attempt: user [{}] tried to update conversation [{}]", userId, conversationId);
            throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETED) {
            throw new ConversationDeletedException("Cannot rename or update a deleted conversation");
        }

        if (request != null) {
            if (request.title() != null) {
                String trimmedTitle = request.title().trim();
                if (trimmedTitle.isBlank()) {
                    throw new IllegalArgumentException("Conversation title cannot be blank");
                }
                conversation.setTitle(trimmedTitle);
            }
            if (request.systemPrompt() != null) {
                conversation.setSystemPrompt(request.systemPrompt().trim());
            }
            if (request.aiModelConfig() != null) {
                conversation.setAiModelConfig(conversationMapper.toAiModelConfig(request.aiModelConfig()));
            }
            if (request.metadata() != null) {
                conversation.setMetadata(new HashMap<>(request.metadata()));
            }
        }

        conversation.setUpdatedAt(Instant.now());
        Conversation updated = conversationRepository.save(conversation);
        log.info("Conversation [{}] updated by user [{}]", conversationId, userId);

        return conversationMapper.toResponse(updated);
    }

    /**
     * Soft-deletes a conversation. Idempotent.
     */
    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.getUser().getId().equals(userId)) {
            log.warn("Unauthorized delete attempt: user [{}] tried to delete conversation [{}]", userId, conversationId);
            throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETED) {
            log.debug("Conversation [{}] was already deleted; skipping idempotent deletion", conversationId);
            return;
        }

        conversation.softDelete();
        conversationRepository.save(conversation);
        log.info("Conversation [{}] soft-deleted by user [{}]", conversationId, userId);
    }
}
