package com.lifeplanner.user;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.security.JwtService;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ApiExceptions.ConflictException("An account with that email already exists.");
        }
        AppUser user = new AppUser(
                request.email().toLowerCase(),
                request.displayName(),
                passwordEncoder.encode(request.password()));
        users.save(user);
        return toResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().toLowerCase(), request.password()));
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect."));
        return toResponse(user);
    }

    private AuthDtos.AuthResponse toResponse(AppUser user) {
        return new AuthDtos.AuthResponse(
                jwtService.issueToken(user.getEmail(), user.getId()),
                user.getId(), user.getDisplayName(), user.getEmail());
    }
}
