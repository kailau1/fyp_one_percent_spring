package com.example.one_percent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public String getAiFeedback(String journalContent) throws Exception {
        OkHttpClient client = new OkHttpClient();

        String requestBody = """
        {
          "model": "gpt-4o-mini",
          "messages": [
            { "role": "system", "content": "You are an assistant that gives supportive and insightful feedback on user journals." },
            { "role": "user", "content": "%s" }
          ],
          "max_tokens": 200,
          "temperature": 0.7
        }
        """.formatted(journalContent);

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API call failed: " + response);
            }

            String responseBody = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);

            return jsonNode
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText();
        }
    }
}
