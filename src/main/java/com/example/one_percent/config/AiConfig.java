package com.example.one_percent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds AiSettings from configuration. Defaults are OpenAI's, so an existing setup with just
 * `openai.api.key` keeps working. To use Gemini, run with the `gemini` profile
 * (application-gemini.properties) and set `ai.api-key`.
 */
@Configuration
public class AiConfig {

    @Bean
    public AiSettings aiSettings(
            @Value("${ai.api-key:${openai.api.key:}}") String apiKey,
            @Value("${ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${ai.smart-model:gpt-4o}") String smartModel,
            @Value("${ai.fast-model:gpt-4o-mini}") String fastModel,
            @Value("${ai.embedding-model:text-embedding-3-small}") String embeddingModel,
            @Value("${ai.reasoning-effort:}") String reasoningEffort) {
        return new AiSettings(apiKey, baseUrl, smartModel, fastModel, embeddingModel,
                reasoningEffort.isBlank() ? null : reasoningEffort);
    }
}
