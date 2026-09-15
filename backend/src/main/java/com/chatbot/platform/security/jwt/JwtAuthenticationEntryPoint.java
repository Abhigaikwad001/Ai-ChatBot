package com.chatbot.platform.security.jwt;

import com.chatbot.platform.api.dto.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles unauthenticated requests by returning a standardized 401 Unauthorized JSON response.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        log.debug("Unauthenticated access attempt on URI: {}", request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String errorMessage = "Full authentication is required to access this resource";
        Object jwtException = request.getAttribute("jwt_exception");
        if (jwtException instanceof Exception ex) {
            errorMessage = "Authentication failed: " + ex.getMessage();
        }

        ApiResponse<Void> apiResponse = ApiResponse.error(errorMessage);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
