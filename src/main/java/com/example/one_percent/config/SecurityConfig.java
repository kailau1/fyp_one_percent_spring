package com.example.one_percent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.example.one_percent.config.WebConfig;

@Configuration
public class SecurityConfig {

    WebConfig webConfig = new WebConfig();
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for APIs
                .cors(cors -> cors.configurationSource(webConfig.corsConfigurationSource())) // Use WebConfig CORS settings
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/journals/**", "/actuator/**",
                                "/swagger-ui/**", "/api-docs/**").permitAll() // Allow unrestricted access
                        .anyRequest().authenticated() // Secure other routes
                );

        return http.build();
    }
}
