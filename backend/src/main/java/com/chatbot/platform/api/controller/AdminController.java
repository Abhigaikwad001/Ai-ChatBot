package com.chatbot.platform.api.controller;

import com.chatbot.platform.api.dto.common.ApiResponse;
import com.chatbot.platform.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller exposing administrative endpoints protected by ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
            "Admin dashboard access authorized",
            Map.of(
                "status", "HEALTHY",
                "adminEmail", SecurityUtils.getCurrentUserEmail()
            )
        ));
    }
}
