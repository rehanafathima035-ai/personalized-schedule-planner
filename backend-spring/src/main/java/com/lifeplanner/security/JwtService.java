package com.lifeplanner.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Stateless token issuing and verification.
 *
 * Stateless on purpose: the same tokens will work for the future React
 * Native client without any server-side session store.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.validity-hours:24}") long validityHours) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be set to at least 32 characters. "
                            + "See .env.example.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofHours(validityHours);
    }

    public String issueToken(String email, Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** Returns the subject (email), or null when the token is invalid or expired. */
    public String extractEmail(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception ex) {
            return null;
        }
    }
}
