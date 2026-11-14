package com.example.one_percent.support;

import com.example.one_percent.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Helpers for putting a user into the security context in plain unit tests. */
public final class TestAuth {

    private TestAuth() {
    }

    public static User user(String id) {
        return User.builder().id(id).email(id + "@example.com").firstName("Test").lastName("User").build();
    }

    public static User loginAs(String id) {
        User user = user(id);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
