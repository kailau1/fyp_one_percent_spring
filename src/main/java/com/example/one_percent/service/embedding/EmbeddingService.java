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
import java.util.Comparator;
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

    /** Overridable so local runs and tests can point at a stand-in server instead of OpenAI. */
    @Value("${openai.base-url:https://api.openai.com}")
    private String baseUrl = "https://api.openai.com";


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
                .url(baseUrl + "/v1/embeddings")
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

    /** How many themes each entry counts towards. */
    public static final int THEMES_PER_ENTRY = 3;

    /**
     * The `n` themes closest to an entry's embedding, closest first. Empty when the embedding is
     * missing or can't be compared with the theme vectors (a different length means a different
     * model, e.g. one made by a local stand-in): those entries simply aren't analysed, rather than
     * being given three arbitrary themes.
     */
    public List<String> closestThemes(List<Double> embedding, int n) {
        if (embedding == null || embedding.isEmpty() || themeEmbeddings.isEmpty()) return List.of();
        int dimensions = themeEmbeddings.values().iterator().next().size();
        if (embedding.size() != dimensions) return List.of();
        return themeEmbeddings.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, List<Double>> theme) -> -cosineSimilarity(embedding, theme.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The user's three most common themes across their entries. */
    public List<String> getFrequentThemes(String userId) {
        Map<String, Integer> themeCounts = new HashMap<>();
        for (JournalEntry entry : journalRepository.findByUserId(userId)) {
            for (String theme : closestThemes(entry.getContentEmbedding(), THEMES_PER_ENTRY)) {
                themeCounts.merge(theme, 1, Integer::sum);
            }
        }
        return themeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
