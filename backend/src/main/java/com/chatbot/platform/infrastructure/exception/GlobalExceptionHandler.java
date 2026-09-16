package com.chatbot.platform.infrastructure.exception;

import com.chatbot.platform.api.dto.common.ApiResponse;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConversationDeletedException.class)
    public ResponseEntity<ApiResponse<Void>> handleConversationDeleted(ConversationDeletedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderTimeout(AiProviderTimeoutException ex) {
        log.warn("AI provider [{}] timed out: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ApiResponse.error("AI provider response timed out. Please try again."));
    }

    @ExceptionHandler(AiProviderUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderUnavailable(AiProviderUnavailableException ex) {
        log.warn("AI provider [{}] unavailable: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error("AI provider is currently unavailable. Please try again later."));
    }

    @ExceptionHandler(AiProviderRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderRateLimit(AiProviderRateLimitException ex) {
        log.warn("AI provider [{}] rate limit reached: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error("AI provider rate limit exceeded. Please wait before retrying."));
    }

    @ExceptionHandler(AiProviderAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderAuthentication(AiProviderAuthenticationException ex) {
        log.error("AI provider [{}] authentication rejected: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("AI provider configuration or authentication error. Please contact administrator."));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderException(AiProviderException ex) {
        log.error("AI provider [{}] execution error: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiResponse.error("AI provider failed to generate a response. Please try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled server exception encountered: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An internal server error occurred"));
    }
}
