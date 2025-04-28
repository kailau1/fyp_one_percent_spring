package com.example.one_percent.service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAIClient {

    private static final String CHAT_COMPLETION_URL = "https://api.openai.com/v1/chat/completions";
    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String apiKey;

    public String sendChatCompletion(ArrayNode messages, String model, int maxTokens, double temperature) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.set("messages", messages);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);

        Request request = new Request.Builder()
                .url(CHAT_COMPLETION_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(payload), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI chat completion failed: " + responseBody);
            }
            JsonNode jsonNode = mapper.readTree(responseBody);
            return jsonNode.get("choices").get(0).get("message").get("content").asText().trim();
        }
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
