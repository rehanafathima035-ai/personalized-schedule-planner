package com.lifeplanner.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.user.AppUser;
import com.lifeplanner.user.UserRepository;

/**
 * Resolves the authenticated user.
 *
 * Every service that touches user data goes through this rather than
 * trusting an id supplied by the client, which is what keeps one user's
 * habits, prayer records and period entries out of another user's reach.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public AppUser require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = ((UserDetails) principal).getUsername();
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Account not found."));
    }

    public Long requireId() {
        return require().getId();
    }
}
