package com.example.one_percent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for development purposes
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/actuator/**",
                                 "/swagger-ui/**", "/api-docs/**")
                            .permitAll() // Allow unrestricted access to API endpoints
                        .anyRequest().authenticated() // Require authentication for all other requests
                )
                .cors(cors -> {}); // Enable CORS configuration (defined separately)

        return http.build();
    }
}
