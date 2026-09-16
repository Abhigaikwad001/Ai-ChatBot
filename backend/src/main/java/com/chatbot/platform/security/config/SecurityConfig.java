package com.chatbot.platform.security.config;

import com.chatbot.platform.security.jwt.JwtAccessDeniedHandler;
import com.chatbot.platform.security.jwt.JwtAuthenticationEntryPoint;
import com.chatbot.platform.security.jwt.JwtAuthenticationFilter;
import com.chatbot.platform.security.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security 6 configuration establishing stateless JWT security,
 * defense-in-depth HTTP response headers, CORS policies, and RBAC rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CustomUserDetailsService customUserDetailsService;
    private final List<String> allowedOrigins;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
        JwtAccessDeniedHandler jwtAccessDeniedHandler,
        CustomUserDetailsService customUserDetailsService,
        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOriginsStr
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.customUserDetailsService = customUserDetailsService;
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // High-security work factor 12 suitable for production password protection
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Strict explicit origins configured via environment (never wildcard '*' in credentialed setups)
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS enabled with strict configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * CSRF Architecture Decision:
             * CSRF protection is safely disabled because this backend is a completely stateless REST API
             * authenticated via JWT Authorization Bearer headers. The API does not rely on ambient browser cookies
             * or HTTP session state for authentication. Therefore, requests originating from foreign cross-sites
             * cannot inject credentials automatically, mitigating the CSRF vulnerability vector.
             */
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session management (no HTTP sessions created or persisted)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Centralized entry points for 401 Unauthorized and 403 Forbidden
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )

            // HTTP Security Headers (anti-clickjacking, MIME-sniffing prevention, referrer policy)
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .cacheControl(Customizer.withDefaults())
            )

            // Authorization Rules
            .authorizeHttpRequests(auth -> auth
                // Allow asynchronous and error dispatches (prevents post-commit AuthorizationDeniedException on SseEmitter completion)
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()

                // Public authentication endpoints (only registration and login)
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()

                // Standalone chatbot APIs (conversations, messages, SSE streaming) accessible without authentication
                .requestMatchers("/api/v1/conversations/**").permitAll()

                // Health & monitoring endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Administrative operations restricted to ROLE_ADMIN
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Service-to-service endpoints restricted to ROLE_ADMIN or ROLE_SERVICE_APP
                .requestMatchers("/api/v1/service/**").hasAnyRole("ADMIN", "SERVICE_APP")

                // All other endpoints require authenticated access
                .anyRequest().authenticated()
            )

            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
