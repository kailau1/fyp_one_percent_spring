package com.example.one_percent.service;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.repository.JournalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@RequiredArgsConstructor
public class EmbeddingSimilarityTest {

    @Autowired
    private JournalRepository journalRepository;
    private EmbeddingService embeddingService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        embeddingService = new EmbeddingService(journalRepository);
        ReflectionTestUtils.setField(embeddingService, "apiKey", "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");

    }

    private double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
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

    @Test
    public void testUserJournalsAgainstAllThemes() throws Exception {
        String userId = "67b8a8b5dd8b77042702f18e";

        InputStream is = getClass().getClassLoader().getResourceAsStream("themes.json");
        Map<String, List<Double>> themeEmbeddings = mapper.readValue(is, new TypeReference<>() {});

        List<JournalEntry> userJournals = journalRepository.findByUserId(userId);
        System.out.println("Found " + userJournals.size() + " journal(s) for user ID " + userId);

        for (JournalEntry journal : userJournals) {
            List<Double> journalEmbedding = journal.getContentEmbedding();
            if (journalEmbedding == null) {
                System.out.println("Skipping journal without embedding: " + journal.getId());
                continue;
            }

            Map<String, Double> results = new TreeMap<>();
            for (Map.Entry<String, List<Double>> entry : themeEmbeddings.entrySet()) {
                String theme = entry.getKey();
                double similarity = cosineSimilarity(journalEmbedding, entry.getValue());
                results.put(theme, similarity);
            }

            System.out.println("Journal ID: " + journal.getId());
            System.out.println(" Cosine similarity scores:");
            results.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(entry -> System.out.printf("• %-18s %.4f%n", entry.getKey(), entry.getValue()));

            System.out.println("=".repeat(60));
        }
    }

    @Test
    public void testFrequentThemesForPersonaA() throws IOException {
        String userId = "67b8a8b5dd8b77042702f18e";

        embeddingService.loadThemeEmbeddings();

        List<String> themes = embeddingService.getFrequentThemes(userId);

        System.out.println("Themes:" + themes);

        assertEquals(3, themes.size());
    }

}