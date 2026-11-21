package com.example.one_percent.service.embedding;

import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.model.JournalEntry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, List<Double>> themeEmbeddings = new HashMap<>();

    private final JournalRepository journalRepository;
    private final OpenAIClient openAIClient;

    /**
     * The theme vectors only make sense next to entry vectors from the same embedding model, so
     * each model has its own file: themes/<model>.json. Generate one for a new model with
     * `scripts/dev-run.sh --maintenance.task=generate-themes`.
     */
    public static String themesResourceFor(String embeddingModel) {
        return "themes/" + embeddingModel.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
    }

    @PostConstruct
    public void loadThemeEmbeddings() {
        String resource = themesResourceFor(openAIClient.settings().embeddingModel());
        try (InputStream is = new ClassPathResource(resource).getInputStream()) {
            Map<String, List<Double>> loadedThemes = new ObjectMapper().readValue(is, new TypeReference<>() {
            });
            themeEmbeddings.putAll(consistentThemes(loadedThemes));
        } catch (IOException e) {
            // Loud on purpose: without themes, every entry looks "not analysed" and nothing else says why.
            logger.error("No theme vectors for embedding model '{}' ({}): theme insights and the AI's theme "
                    + "context are off. Generate them with: scripts/dev-run.sh --maintenance.task=generate-themes",
                    openAIClient.settings().embeddingModel(), resource);
        }
    }

    /** How many numbers the theme vectors have (0 if none are loaded). Entries must match to be compared. */
    public int themeDimensions() {
        return themeEmbeddings.isEmpty() ? 0 : themeEmbeddings.values().iterator().next().size();
    }

    /**
     * Keeps only the theme vectors with the most common length: one vector of another length would
     * score 0 against every entry and be picked arbitrarily on ties.
     */
    public static Map<String, List<Double>> consistentThemes(Map<String, List<Double>> themes) {
        Map<Integer, Long> lengths = themes.values().stream().collect(Collectors.groupingBy(List::size, Collectors.counting()));
        int dimensions = lengths.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
        Map<String, List<Double>> kept = new HashMap<>();
        themes.forEach((name, vector) -> {
            if (vector.size() == dimensions) kept.put(name, vector);
            else logger.error("Ignoring theme '{}': its vector has {} numbers, the others {}", name, vector.size(), dimensions);
        });
        return kept;
    }

    public List<Double> generateEmbedding(String inputText) throws IOException {
        return openAIClient.embed(inputText);
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
        if (embedding.size() != themeDimensions()) return List.of();
        Map<String, Double> scores = new HashMap<>(); // each score once, not once per comparison
        themeEmbeddings.forEach((name, vector) -> scores.put(name, cosineSimilarity(embedding, vector)));
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Entries grouped by theme: each analysed entry counts towards its THEMES_PER_ENTRY closest themes. */
    public record ThemeGroups(int analysedEntries, List<ThemeGroup> themes) {
    }

    /** One theme and the entries touching on it, in the order they were given. */
    public record ThemeGroup(String name, List<JournalEntry> entries) {
    }

    /**
     * The one place that decides which themes a set of entries is about: the insights page and the
     * AI's "the user often writes about" context both read it, so they can't disagree. Themes come
     * most common first (ties by name); entries without a usable embedding aren't analysed.
     */
    public ThemeGroups groupByTheme(List<JournalEntry> entries) {
        Map<String, List<JournalEntry>> byTheme = new HashMap<>();
        int analysed = 0;
        for (JournalEntry entry : entries) {
            List<String> themes = closestThemes(entry.getContentEmbedding(), THEMES_PER_ENTRY);
            if (themes.isEmpty()) continue;
            analysed++;
            for (String theme : themes) {
                byTheme.computeIfAbsent(theme, t -> new ArrayList<>()).add(entry);
            }
        }
        List<ThemeGroup> groups = byTheme.entrySet().stream()
                .map(e -> new ThemeGroup(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt((ThemeGroup g) -> g.entries().size()).reversed().thenComparing(ThemeGroup::name))
                .toList();
        return new ThemeGroups(analysed, groups);
    }

    /** The user's three most common themes across their entries. */
    public List<String> getFrequentThemes(String userId) {
        return groupByTheme(journalRepository.findForThemes(userId)).themes().stream()
                .limit(3)
                .map(ThemeGroup::name)
                .toList();
    }
}
