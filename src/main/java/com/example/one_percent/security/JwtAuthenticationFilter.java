package com.example.one_percent.security;

import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        System.out.println("[JWT Filter] Incoming request: " + request.getMethod() + " " + request.getRequestURI());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            System.out.println("[JWT Filter] Bearer token found: " + token);

            String email = jwtUtil.extractEmail(token);
            System.out.println("[JWT Filter] Extracted email: " + email);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmail(email);

                if (user != null) {
                    System.out.println("[JWT Filter] User found: " + user.getEmail());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.emptyList()
                            );
                    authentication.setDetails(user);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("[JWT Filter] Authentication set for user.");
                } else {
                    System.out.println("[JWT Filter] No user found for email: " + email);
                }
            } else if (email == null) {
                System.out.println("[JWT Filter] Failed to extract email from token.");
            } else {
                System.out.println("[JWT Filter] Authentication already exists in context.");
            }
        } else {
            System.out.println("[JWT Filter] No Authorization header or does not start with Bearer.");
        }

        filterChain.doFilter(request, response);
    }
}
