package com.chatbot.platform.security.service;

import com.chatbot.platform.api.dto.auth.AuthResponse;
import com.chatbot.platform.api.dto.auth.LoginRequest;
import com.chatbot.platform.api.dto.auth.RegisterRequest;
import com.chatbot.platform.api.dto.user.UserResponse;
import com.chatbot.platform.api.mapper.UserMapper;
import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import com.chatbot.platform.core.repository.UserRepository;
import com.chatbot.platform.infrastructure.exception.AccountDeactivatedException;
import com.chatbot.platform.infrastructure.exception.AccountSuspendedException;
import com.chatbot.platform.infrastructure.exception.DuplicateEmailException;
import com.chatbot.platform.infrastructure.exception.RateLimitExceededException;
import com.chatbot.platform.infrastructure.exception.ResourceNotFoundException;
import com.chatbot.platform.security.jwt.JwtProperties;
import com.chatbot.platform.security.jwt.JwtService;
import com.chatbot.platform.security.principal.UserPrincipal;
import com.chatbot.platform.security.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Core authentication service coordinating registration, login, credential verification,
 * account status enforcement, and JWT generation.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final RateLimiter rateLimiter;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        JwtProperties jwtProperties,
        UserMapper userMapper,
        AuthenticationManager authenticationManager,
        RateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Registers a new user account with normalized email and BCrypt hashed password.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt rejected: email already exists for {}", normalizedEmail);
            throw new DuplicateEmailException("Email is already registered: " + normalizedEmail);
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(normalizedEmail, hashedPassword, request.fullName().trim());
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        log.info("New user account created successfully with ID: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    /**
     * Authenticates credentials through AuthenticationManager, verifies account status,
     * and generates a signed JWT token.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (!rateLimiter.tryAcquire(normalizedEmail)) {
            log.warn("Login attempt blocked by rate limiter for: {}", normalizedEmail);
            throw new RateLimitExceededException("Too many failed login attempts. Please try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            // Explicit secondary status validation
            if (principal.getStatus() == UserStatus.SUSPENDED) {
                log.warn("Login rejected for suspended account: {}", principal.getId());
                throw new AccountSuspendedException("Account is suspended. Please contact administrator.");
            }
            if (principal.getStatus() == UserStatus.DEACTIVATED) {
                log.warn("Login rejected for deactivated account: {}", principal.getId());
                throw new AccountDeactivatedException("Account is deactivated.");
            }

            // Authentication succeeded, reset failure tracker
            rateLimiter.reset(normalizedEmail);

            String token = jwtService.generateToken(principal);
            User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

            UserResponse userResponse = userMapper.toResponse(user);
            log.info("User logged in successfully: {}", principal.getId());

            return AuthResponse.of(token, jwtProperties.getAccessTokenExpirationMs(), userResponse);

        } catch (BadCredentialsException ex) {
            rateLimiter.recordFailure(normalizedEmail);
            log.warn("Failed authentication attempt for email: {}", normalizedEmail);
            throw ex;
        } catch (LockedException ex) {
            log.warn("Login rejected due to locked/suspended status for: {}", normalizedEmail);
            throw new AccountSuspendedException("Account is suspended. Please contact administrator.");
        } catch (DisabledException ex) {
            log.warn("Login rejected due to disabled/deactivated status for: {}", normalizedEmail);
            throw new AccountDeactivatedException("Account is deactivated.");
        }
    }

    /**
     * Retrieves the profile response for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toResponse(user);
    }
}
