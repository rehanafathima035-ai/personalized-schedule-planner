package com.lifeplanner.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for authentication. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @Email(message = "must be a valid email address")
            @NotBlank String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 128, message = "must be at least 8 characters")
            String password) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record AuthResponse(String token, Long userId, String displayName, String email) {}
}
