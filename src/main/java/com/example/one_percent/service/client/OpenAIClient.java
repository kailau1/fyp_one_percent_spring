package com.example.one_percent.service.client;

import com.example.one_percent.config.AiSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Talks to any service with an OpenAI-compatible API (OpenAI itself, Google Gemini, Ollama...).
 * Which service and which models come from AiSettings; nothing here is specific to one provider.
 */
@Component
public class OpenAIClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);

    private static final MediaType JSON = MediaType.parse("application/json");

    private final AiSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    // One shared client (it pools connections). The read timeout stays under the web app's 60s AI
    // timeout, so a hung model fails here first and there's time to fall back to the fast model.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build();

    public OpenAIClient(AiSettings settings) {
        this.settings = settings;
    }

    public AiSettings settings() {
        return settings;
    }

    /**
     * A chat reply from the model for `model`'s role. If the smart model is overloaded (HTTP 503),
     * out of quota (HTTP 429: Gemini's free tier allows the Flash models only ~20 requests a day,
     * Flash-Lite 500) or doesn't answer in time, the request is retried once with the fast model:
     * a slightly plainer answer beats an error.
     */
    public String sendChatCompletion(ArrayNode messages, AiModel model, int maxTokens, double temperature) throws IOException {
        try {
            return chat(messages, model, maxTokens, temperature);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            boolean busy = e instanceof SocketTimeoutException || message.contains("HTTP 503") || message.contains("HTTP 429");
            boolean canFallBack = model == AiModel.SMART && !settings.fastModel().equals(settings.smartModel());
            if (!busy || !canFallBack) throw e;
            logger.warn("Smart model '{}' unavailable ({}); answering with '{}' instead",
                    settings.smartModel(), e.getMessage(), settings.fastModel());
            return chat(messages, AiModel.FAST, maxTokens, temperature);
        }
    }

    private String chat(ArrayNode messages, AiModel model, int maxTokens, double temperature) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        boolean smart = model == AiModel.SMART;
        payload.put("model", smart ? settings.smartModel() : settings.fastModel());
        payload.set("messages", messages);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);
        String effort = smart ? settings.smartReasoningEffort() : settings.fastReasoningEffort();
        if (effort != null) {
            payload.put("reasoning_effort", effort);
        }

        JsonNode response = post("/chat/completions", payload, "chat completion");
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            // e.g. a safety block, or a thinking model that used the whole token budget thinking
            String reason = response.path("choices").path(0).path("finish_reason").asText("unknown");
            throw new IOException("The AI returned no text (finish reason: " + reason + ")");
        }
        return content.asText().trim();
    }

    /** One text as a vector. */
    public List<Double> embed(String text) throws IOException {
        return embedAll(List.of(text)).get(0);
    }

    /** Several texts as vectors in one request, in the same order as the texts. */
    public List<List<Double>> embedAll(List<String> texts) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", settings.embeddingModel());
        if (settings.embeddingDimensions() != null) {
            payload.put("dimensions", settings.embeddingDimensions());
        }
        ArrayNode input = payload.putArray("input");
        texts.forEach(input::add);

        JsonNode data = post("/embeddings", payload, "embedding").path("data");
        if (!data.isArray() || data.size() != texts.size()) {
            throw new IOException("The AI returned " + data.size() + " embeddings for " + texts.size() + " texts");
        }
        List<List<Double>> vectors = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) vectors.add(null);
        for (JsonNode item : data) {
            // each item says which input it belongs to; don't assume the order
            int index = item.path("index").asInt(vectors.indexOf(null));
            List<Double> vector = new ArrayList<>();
            item.path("embedding").forEach(value -> vector.add(value.asDouble()));
            vectors.set(index, vector);
        }
        return vectors;
    }

    private JsonNode post(String path, ObjectNode payload, String what) throws IOException {
        Request.Builder request = new Request.Builder()
                .url(settings.baseUrl() + path)
                .post(RequestBody.create(mapper.writeValueAsString(payload), JSON));
        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
            request.addHeader("Authorization", "Bearer " + settings.apiKey());
        }

        try (Response response = client.newCall(request.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // the provider's message explains quota, bad model names etc.; never log our key
                throw new IOException("AI " + what + " failed with HTTP " + response.code() + ": " + abbreviate(body));
            }
            return mapper.readTree(body);
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    public ArrayNode createMessages(List<ObjectNode> messageNodes) {
        ArrayNode array = mapper.createArrayNode();
        messageNodes.forEach(array::add);
        return array;
    }

    public ObjectNode createSingleMessage(String role, String content) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}
