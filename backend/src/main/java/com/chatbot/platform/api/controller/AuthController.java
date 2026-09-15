package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.auth.AuthResponse;
import com.chatbot.platform.api.dto.auth.LoginRequest;
import com.chatbot.platform.api.dto.auth.RegisterRequest;
import com.chatbot.platform.api.dto.common.ApiResponse;
import com.chatbot.platform.api.dto.user.UserResponse;
import com.chatbot.platform.security.service.AuthService;
import com.chatbot.platform.security.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Versioned authentication REST controller exposing public registration, login,
 * and authenticated user profile retrieval.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new standard user account.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("User registered successfully", response));
    }

    /**
     * Authenticates user credentials and issues a signed JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Retrieves the profile of the currently authenticated user from SecurityContext.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserResponse response = authService.getCurrentUser(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
