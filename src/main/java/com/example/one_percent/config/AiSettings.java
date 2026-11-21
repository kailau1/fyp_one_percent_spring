package com.example.one_percent.config;

/**
 * Which AI service the app talks to, and which of its models it uses for what. Any service with an
 * OpenAI-compatible API works (OpenAI, Google Gemini, a local Ollama...): only these settings change.
 *
 * @param apiKey                sent as a Bearer token; empty for services that need none (e.g. Ollama)
 * @param baseUrl               the API prefix the paths are appended to, e.g. "https://api.openai.com/v1"
 * @param smartModel            for work the user reads closely: journal feedback, writing prompts
 * @param fastModel             for short, cheap jobs: the tip of the day, feedback summaries
 * @param embeddingModel        turns text into vectors for similarity and theme detection. Changing it
 *                              makes existing vectors incomparable: see MaintenanceRunner.
 * @param embeddingDimensions   optional vector size ("dimensions"), for models that can shorten their
 *                              vectors (e.g. 768 for gemini-embedding-2: same accuracy, a quarter of the storage)
 * @param smartReasoningEffort  optional "reasoning_effort" for the smart model, and
 * @param fastReasoningEffort   for the fast model. Models that think before answering count the thinking
 *                              against max_tokens, so short replies need it low; but models disagree on
 *                              accepted values (gemini-3.8-flash: "none" only; gemini-3.5-flash-lite:
 *                              "minimal", rejects "none"), hence one per role.
 */
public record AiSettings(
        String apiKey,
        String baseUrl,
        String smartModel,
        String fastModel,
        String embeddingModel,
        Integer embeddingDimensions,
        String smartReasoningEffort,
        String fastReasoningEffort) {

    public AiSettings {
        baseUrl = stripTrailingSlash(baseUrl);
    }

    /** OpenAI with the models the app was built on: the defaults when nothing is configured. */
    public static AiSettings openAiDefaults(String apiKey, String baseUrl) {
        return new AiSettings(apiKey, baseUrl, "gpt-4o", "gpt-4o-mini", "text-embedding-3-small", null, null, null);
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
