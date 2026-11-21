package com.example.one_percent.service.client;

import com.example.one_percent.config.AiSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Talks to any service with an OpenAI-compatible API (OpenAI itself, Google Gemini, Ollama...).
 * Which service and which models come from AiSettings; nothing here is specific to one provider.
 */
@Component
public class OpenAIClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final AiSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    // One shared client (it pools connections). Generous read timeout: feedback can take a while.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    public OpenAIClient(AiSettings settings) {
        this.settings = settings;
    }

    public AiSettings settings() {
        return settings;
    }

    public String sendChatCompletion(ArrayNode messages, AiModel model, int maxTokens, double temperature) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model == AiModel.SMART ? settings.smartModel() : settings.fastModel());
        payload.set("messages", messages);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);
        if (settings.reasoningEffort() != null) {
            payload.put("reasoning_effort", settings.reasoningEffort());
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
