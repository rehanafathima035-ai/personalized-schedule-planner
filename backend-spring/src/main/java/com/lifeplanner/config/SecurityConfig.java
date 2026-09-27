package com.lifeplanner.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.lifeplanner.security.JwtAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String allowedOrigins;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${app.cors.allowed-origins:http://localhost:5173}")
            String allowedOrigins) {

        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config)
            throws Exception {

        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration cors =
                new CorsConfiguration();

        List<String> origins =
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList();

        cors.setAllowedOrigins(origins);

        cors.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        cors.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        cors.setExposedHeaders(List.of(
                "Authorization"
        ));

        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                cors);

        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http)
            throws Exception {

        http
            .cors(cors ->
                    cors.configurationSource(
                            corsConfigurationSource()))

            .csrf(csrf ->
                    csrf.disable())

            .sessionManagement(session ->
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS))

            .exceptionHandling(exception ->
                    exception
                        .authenticationEntryPoint(
                                (request, response, authException) -> {
                                    response.setStatus(401);
                                    response.setContentType(
                                            "application/json");
                                    response.getWriter().write(
                                            "{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required.\"}");
                                })

                        .accessDeniedHandler(
                                (request, response, accessDeniedException) -> {
                                    response.setStatus(403);
                                    response.setContentType(
                                            "application/json");
                                    response.getWriter().write(
                                            "{\"error\":\"FORBIDDEN\",\"message\":\"Access denied.\"}");
                                }))

            .authorizeHttpRequests(auth ->
                    auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                "/health",
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .anyRequest()
                        .authenticated())

            .addFilterBefore(
                    jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}