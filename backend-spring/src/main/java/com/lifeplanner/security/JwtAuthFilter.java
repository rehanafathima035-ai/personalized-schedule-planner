package com.lifeplanner.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthFilter(
            JwtService jwtService,
            AppUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header =
                request.getHeader("Authorization");

        if (header == null
                || !header.startsWith("Bearer ")
                || SecurityContextHolder
                        .getContext()
                        .getAuthentication() != null) {

            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String email =
                jwtService.extractEmail(token);

        if (email != null && !email.isBlank()) {

            try {
                UserDetails details =
                        userDetailsService
                                .loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                details,
                                null,
                                details.getAuthorities());

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

            } catch (RuntimeException ignored) {
                // Invalid/non-existent user remains unauthenticated.
            }
        }

        chain.doFilter(request, response);
    }
}