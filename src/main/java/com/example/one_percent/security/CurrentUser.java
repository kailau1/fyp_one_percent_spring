package com.example.one_percent.security;

import com.example.one_percent.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user the current request is authenticated as (set by JwtAuthenticationFilter). Older services
 * each carry a private copy of this lookup; new code should use this one.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** @throws IllegalStateException if the request isn't authenticated (a security config mistake: a 500). */
    public static User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user");
    }
}
