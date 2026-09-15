package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.common.ApiResponse;
import com.chatbot.platform.api.dto.common.PageResponse;
import com.chatbot.platform.api.dto.conversation.ConversationResponse;
import com.chatbot.platform.api.dto.conversation.ConversationSummaryResponse;
import com.chatbot.platform.api.dto.conversation.CreateConversationRequest;
import com.chatbot.platform.api.dto.conversation.UpdateConversationRequest;
import com.chatbot.platform.core.service.ConversationService;
import com.chatbot.platform.security.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for authenticated conversation lifecycle operations:
 * creation, listing, retrieval, renaming, and soft-deletion.
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Creates a new conversation for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
        @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ConversationResponse response = conversationService.createConversation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Conversation created successfully", response));
    }

    /**
     * Lists active conversations owned by the authenticated user, ordered by most recently active.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ConversationSummaryResponse>>> listConversations(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        PageResponse<ConversationSummaryResponse> response = conversationService.listConversations(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves a single conversation by ID with messages, ensuring user ownership.
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
        @PathVariable("conversationId") UUID conversationId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ConversationResponse response = conversationService.getConversation(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Renames or updates conversation settings. Only the owner can update.
     */
    @PatchMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateConversation(
        @PathVariable("conversationId") UUID conversationId,
        @Valid @RequestBody UpdateConversationRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ConversationResponse response = conversationService.updateConversation(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Conversation updated successfully", response));
    }

    /**
     * Soft-deletes a conversation. Only the owner can delete.
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
        @PathVariable("conversationId") UUID conversationId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        conversationService.deleteConversation(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully", null));
    }
}
