package com.example.one_percent.service.embedding;

import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.model.JournalEntry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, List<Double>> themeEmbeddings = new HashMap<>();

    private final JournalRepository journalRepository;


    @Value("${openai.api.key}")
    private String apiKey;


    @PostConstruct
    public void loadThemeEmbeddings() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("themes.json").getInputStream();
            Map<String, List<Double>> loadedThemes = mapper.readValue(is, new TypeReference<>() {
            });
            themeEmbeddings.putAll(loadedThemes);
        } catch (IOException e) {
            System.out.println("Failed to load theme embeddings from JSON");
        }
    }

    public List<Double> generateEmbedding(String inputText) throws IOException {
        OkHttpClient client = new OkHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", "text-embedding-3-small");
        payload.put("input", inputText); // Proper escaping done by ObjectMapper

        String jsonPayload = mapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonPayload, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI Embedding API failed: " + responseBody);
            }

            JsonNode jsonNode = mapper.readTree(responseBody);
            JsonNode vectorNode = jsonNode.get("data").get(0).get("embedding");

            List<Double> embedding = new ArrayList<>();
            for (JsonNode val : vectorNode) {
                embedding.add(val.asDouble());
            }
            return embedding;
        }
    }


    public double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA.size() != vecB.size()) return 0.0;

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vecA.size(); i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            magnitudeA += Math.pow(vecA.get(i), 2);
            magnitudeB += Math.pow(vecB.get(i), 2);
        }

        if (magnitudeA == 0 || magnitudeB == 0) return 0.0;

        return dotProduct / (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }

    public List<String> getFrequentThemes(String userId) {
        List<JournalEntry> entries = journalRepository.findByUserId(userId);
        Map<String, Integer> themeCounts = new HashMap<>();

        for (JournalEntry entry : entries) {
            List<Double> userEmb = entry.getContentEmbedding();
            if (userEmb == null) continue;

            Map<String, Double> similarities = new HashMap<>();
            for (Map.Entry<String, List<Double>> theme : themeEmbeddings.entrySet()) {
                double similarity = cosineSimilarity(userEmb, theme.getValue());
                similarities.put(theme.getKey(), similarity);
            }

            similarities.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .forEach(entryTop -> {
                        themeCounts.put(entryTop.getKey(), themeCounts.getOrDefault(entryTop.getKey(), 0) + 1);
                    });
        }

        return themeCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

    }


}

