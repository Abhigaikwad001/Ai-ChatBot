package com.chatbot.platform.infrastructure.exception;

import com.chatbot.platform.api.dto.common.ApiResponse;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        // Generic message prevents account enumeration attacks
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountSuspended(AccountSuspendedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Account is suspended. Please contact administrator."));
    }

    @ExceptionHandler(AccountDeactivatedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountDeactivated(AccountDeactivatedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Account is deactivated."));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccountLockedOrDisabled(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Account access is restricted. Please contact administrator."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied: insufficient permissions or unowned resource"));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

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

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Authentication failed: " + ex.getMessage()));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtException(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Invalid or expired authentication token"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error("Too many attempts: " + ex.getMessage()));
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

    @ExceptionHandler(com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderTimeout(com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException ex) {
        log.warn("AI provider [{}] timed out: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ApiResponse.error("AI provider response timed out. Please try again."));
    }

    @ExceptionHandler(com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderUnavailable(com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException ex) {
        log.warn("AI provider [{}] unavailable: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error("AI provider is currently unavailable. Please try again later."));
    }

    @ExceptionHandler(com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderRateLimit(com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException ex) {
        log.warn("AI provider [{}] rate limit reached: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error("AI provider rate limit exceeded. Please wait before retrying."));
    }

    @ExceptionHandler(com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderAuthentication(com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException ex) {
        log.error("AI provider [{}] authentication rejected: {}", ex.getProviderName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("AI provider configuration or authentication error. Please contact administrator."));
    }

    @ExceptionHandler(com.chatbot.platform.infrastructure.exception.ai.AiProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderException(com.chatbot.platform.infrastructure.exception.ai.AiProviderException ex) {
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
