package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.common.ApiResponse;
import com.chatbot.platform.api.dto.common.PageResponse;
import com.chatbot.platform.api.dto.conversation.ConversationResponse;
import com.chatbot.platform.api.dto.conversation.ConversationSummaryResponse;
import com.chatbot.platform.api.dto.conversation.CreateConversationRequest;
import com.chatbot.platform.api.dto.conversation.UpdateConversationRequest;
import com.chatbot.platform.core.service.ConversationService;
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
 * REST controller for standalone conversation lifecycle operations:
 * creation, listing, retrieval, renaming, and soft-deletion without requiring authentication.
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Creates a new conversation in standalone mode.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
        @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        ConversationResponse response = conversationService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Conversation created successfully", response));
    }

    /**
     * Lists active conversations ordered by most recently active in standalone mode.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ConversationSummaryResponse>>> listConversations(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageResponse<ConversationSummaryResponse> response = conversationService.listConversations(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves a single conversation by ID with messages in standalone mode.
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
        @PathVariable("conversationId") UUID conversationId
    ) {
        ConversationResponse response = conversationService.getConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Renames or updates conversation settings in standalone mode.
     */
    @PatchMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateConversation(
        @PathVariable("conversationId") UUID conversationId,
        @Valid @RequestBody UpdateConversationRequest request
    ) {
        ConversationResponse response = conversationService.updateConversation(conversationId, request);
        return ResponseEntity.ok(ApiResponse.success("Conversation updated successfully", response));
    }

    /**
     * Soft-deletes a conversation in standalone mode.
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
        @PathVariable("conversationId") UUID conversationId
    ) {
        conversationService.deleteConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully", null));
    }
}
