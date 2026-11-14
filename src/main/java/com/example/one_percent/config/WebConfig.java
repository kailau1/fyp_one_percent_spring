package com.example.one_percent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class WebConfig {

    /**
     * Browser origins allowed to call the API, e.g. the web app's dev server. Override with
     * app.cors.allowed-origins=https://your-site.example in application.properties.
     * (Native mobile apps aren't browsers and aren't subject to CORS.)
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173,http://localhost:8081}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Auth is a Bearer token in a header, not a cookie, so credentials mode isn't needed.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
