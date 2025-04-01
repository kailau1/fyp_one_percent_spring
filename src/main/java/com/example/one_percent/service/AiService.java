package com.example.one_percent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public String getAiFeedback(String journalContent) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // <-- Increase this if response takes long
                .build();
        String safeJournalContent = journalContent.replace("\n", " ").replace("\r", " ").trim();

        String requestBody = String.format("""
    {
      "model": "gpt-4o",
      "messages": [
        {
          "role": "system",
          "content": "You are an AI assistant that analyzes user journal entries and provides supportive, constructive feedback. Your goal is to help users reflect, grow, and take practical steps toward self-improvement. Structure your response in three concise sections: 1. Strengths – Highlight the user’s positive traits, mindsets, or actions reflected in the entry. Be specific and affirming. 2. Challenges & Insights – Briefly identify any challenges mentioned and, where appropriate, connect them to psychological principles such as cognitive biases, habit loops, or emotional regulation. 3. Actionable Strategies – Offer clear, science-backed suggestions tailored to the user’s situation. Focus on practical techniques from positive psychology, productivity, or behavior change. Use a friendly and encouraging tone. Avoid generic praise or overly abstract advice. Always speak directly to the user. Word Limit: 150 words."
        },
        {
          "role": "user",
          "content": "%s"
        }
      ],
      "max_tokens": 300,
      "temperature": 0.7
    }
    """, safeJournalContent);

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        logger.debug("Request payload: {}", requestBody);
        logger.debug("Request headers: Authorization=Bearer {}, Content-Type=application/json", apiKey);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            logger.debug("Response body: {}", responseBody);

            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API call failed: " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);

            return jsonNode
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText()
                    .trim();
        }
    }

    public String getDailyTip() throws IOException {
        OkHttpClient client = new OkHttpClient();

        String requestBody = """
        {
          "model": "gpt-4o-mini",
          "messages": [
            {
              "role": "system",
              "content": "You are a helpful and inspiring assistant that provides concise, motivational self-improvement tips for users working on personal growth, habit-building, and positive psychology.\\n\\nYour tips should be:\\n\\n- Original and non-generic (avoid clichés like \\"start small\\" or \\"be consistent\\")\\n- Focused on actionable strategies, fresh mindsets, or lesser-known psychological principles\\n- Friendly, warm, and encouraging in tone\\n- 1–2 sentences max\\n- Don't include \\"Tip of the Day\\" or greetings. Just return the tip.\\n\\nDo NOT repeat previously suggested ideas like:\\n- \\"Start small and be consistent\\"\\n- \\"Drink water in the morning\\"\\n- \\"Read for five minutes daily\\"\\n- \\"Celebrate small wins\\"\\n\\nHere are examples of the kind of variety and voice we're aiming for:\\n\\n- \\"Make your environment your ally. Leave your journal on your pillow to remind yourself to write before bed.\\"\\n- \\"Don’t break the chain. Use a simple calendar to mark off each day you stick to your habit.\\"\\n- \\"If your mornings feel rushed, prepare your clothes and to-do list the night before. Clarity lowers resistance.\\"\\n- \\"Self-kindness fuels growth. When you slip up, talk to yourself like you would a close friend.\\"\\n- \\"Don’t rely on motivation—build frictionless systems that make the right choice the easiest one.\\"\\n\\nNow, generate a **new, unique self-improvement tip** following the same style."
            },
            {
              "role": "user",
              "content": "Give me one tip of the day to help with self-development and building better habits."
            }
          ],
          "max_tokens": 60,
          "temperature": 0.8
        }
        """;

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        logger.debug("Request payload: {}", requestBody);
        logger.debug("Request headers: Authorization=Bearer {}, Content-Type=application/json", apiKey);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            logger.debug("Response body: {}", responseBody);

            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API call failed: " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);

            return jsonNode
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText()
                    .trim();
        }
    }

    public List<String> getPromptSuggestions(String context) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        String userInstruction = context.isEmpty()
                ? "Generate 6–8 unique journaling prompts to help users reflect on their self-development."
                : "Generate 6–8 unique journaling prompts to help users reflect on their self-development, specifically around: " + context;

        String requestBody = String.format("""
    {
      "model": "gpt-4o-mini",
      "messages": [
        {
          "role": "system",
          "content": "You are a journaling prompt generator. Given a context or theme, generate 6–8 unique, non-repetitive prompts for self-reflection. Do not include numbering. Just return the prompts, each on a new line. Make them open-ended, thought-provoking, and varied."
        },
        {
          "role": "user",
          "content": "%s"
        }
      ],
      "temperature": 0.8,
      "max_tokens": 400
    }
    """, userInstruction);

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API call failed: " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);
            String content = jsonNode.get("choices").get(0).get("message").get("content").asText().trim();

            return Arrays.stream(content.split("\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }
    }


}