package com.example.one_percent.service.embedding;

import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.model.JournalEntry;

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
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    /** How many themes an entry is tagged with, at most. */
    public static final int MAX_THEMES_PER_ENTRY = 3;
    /** Used when a vectors file has no calibrated margin. */
    static final double DEFAULT_SECONDARY_MARGIN = 0.02;

    private final Map<String, List<Double>> themeEmbeddings = new HashMap<>();
    private final ThemeDefinitions definitions = ThemeDefinitions.load();
    /** null for legacy vectors (no calibration): then every comparable entry gets its 3 closest themes. */
    private Double minSimilarity;
    private double secondaryMargin = DEFAULT_SECONDARY_MARGIN;
    /** Identifies the loaded vectors; stored with each entry's tags so stale tags can be spotted. */
    private String themesVersion;

    private final JournalRepository journalRepository;
    private final OpenAIClient openAIClient;

    /**
     * The theme vectors only make sense next to entry vectors from the same embedding model and
     * size, so each has its own file: themes/<model>[-<dims>].json. Make one for a new model with
     * `scripts/dev-run.sh --maintenance.task=generate-themes`.
     */
    public static String themesResourceFor(String embeddingModel, Integer dimensions) {
        String name = embeddingModel.replaceAll("[^A-Za-z0-9._-]", "_") + (dimensions != null ? "-" + dimensions : "");
        return "themes/" + name + ".json";
    }

    @PostConstruct
    public void loadThemeEmbeddings() {
        String resource = themesResourceFor(openAIClient.settings().embeddingModel(), openAIClient.settings().embeddingDimensions());
        try (InputStream is = new ClassPathResource(resource).getInputStream()) {
            useVectors(ThemeVectors.read(is), resource);
        } catch (IOException e) {
            // Loud on purpose: without themes, every entry looks "not analysed" and nothing else says why.
            logger.error("No theme vectors for embedding model '{}' ({}): theme insights and the AI's theme "
                    + "context are off. Generate them with: scripts/dev-run.sh --maintenance.task=generate-themes",
                    openAIClient.settings().embeddingModel(), resource);
        }
    }

    /** Switches to a set of theme vectors (also used by tests and the maintenance tasks). */
    public void useVectors(ThemeVectors vectors, String source) {
        themeEmbeddings.clear();
        themeEmbeddings.putAll(consistentThemes(vectors.themes()));
        minSimilarity = vectors.minSimilarity();
        secondaryMargin = vectors.secondaryMargin() != null ? vectors.secondaryMargin() : DEFAULT_SECONDARY_MARGIN;
        themesVersion = source + ":" + (vectors.isLegacy() ? "legacy" : vectors.definitionsHash());
        if (!vectors.isLegacy() && !definitions.hash().equals(vectors.definitionsHash())) {
            logger.warn("{} was generated from older theme definitions; regenerate it with "
                    + "--maintenance.task=generate-themes to pick up the changes", source);
        }
    }

    public ThemeDefinitions definitions() {
        return definitions;
    }

    /** The names an entry can be tagged with (for pickers). */
    public List<String> themeNames() {
        return definitions.names();
    }

    public String themesVersion() {
        return themesVersion;
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

    /** How many numbers the theme vectors have (0 if none are loaded). Entries must match to be compared. */
    public int themeDimensions() {
        return themeEmbeddings.isEmpty() ? 0 : themeEmbeddings.values().iterator().next().size();
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

    /**
     * The themes an embedding is clearly about, best first, with their scores. With calibrated
     * vectors: the best theme if it reaches minSimilarity, plus up to two more that also reach it and
     * are within secondaryMargin of the best. Everyday entries get none. With legacy vectors: the three
     * closest. Empty when the embedding is missing or from another model (a different length).
     */
    public LinkedHashMap<String, Double> tagThemes(List<Double> embedding) {
        LinkedHashMap<String, Double> tags = new LinkedHashMap<>();
        if (embedding == null || embedding.isEmpty() || themeEmbeddings.isEmpty()) return tags;
        if (embedding.size() != themeDimensions()) return tags;

        List<Map.Entry<String, Double>> ranked = themeEmbeddings.entrySet().stream()
                .map(theme -> Map.entry(theme.getKey(), cosineSimilarity(embedding, theme.getValue())))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
        double best = ranked.get(0).getValue();
        for (Map.Entry<String, Double> theme : ranked) {
            if (tags.size() == MAX_THEMES_PER_ENTRY) break;
            boolean keep = minSimilarity == null
                    || (theme.getValue() >= minSimilarity && best - theme.getValue() <= secondaryMargin);
            if (!keep) break; // ranked best first, so nothing further qualifies
            tags.put(theme.getKey(), theme.getValue());
        }
        return tags;
    }

    /** Stores the entry's automatic tags (call after its embedding changes). */
    public void tag(JournalEntry entry) {
        LinkedHashMap<String, Double> tags = tagThemes(entry.getContentEmbedding());
        boolean comparable = entry.getContentEmbedding() != null && entry.getContentEmbedding().size() == themeDimensions();
        entry.setThemeScores(comparable ? tags : null);
        entry.setThemesVersion(comparable ? themesVersion : null);
    }

    /** The automatic tags: stored ones if made with the current vectors, otherwise worked out again. */
    public List<String> automaticThemes(JournalEntry entry) {
        boolean current = entry.getThemeScores() != null && Objects.equals(entry.getThemesVersion(), themesVersion);
        if (current || entry.getContentEmbedding() == null) {
            if (entry.getThemeScores() == null) return List.of();
            return entry.getThemeScores().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
        }
        return List.copyOf(tagThemes(entry.getContentEmbedding()).keySet());
    }

    /** The themes that count: the automatic ones, minus any the user removed, plus any they added. */
    public List<String> effectiveThemes(JournalEntry entry) {
        List<String> themes = new ArrayList<>(automaticThemes(entry));
        JournalEntry.ThemeOverrides overrides = entry.getThemeOverrides();
        if (overrides != null) {
            if (overrides.getRemoved() != null) themes.removeAll(overrides.getRemoved());
            if (overrides.getAdded() != null) {
                overrides.getAdded().stream().filter(theme -> !themes.contains(theme)).forEach(themes::add);
            }
        }
        return themes;
    }

    /** Whether the entry's themes are known: it could be compared with the themes, or the user tagged it. */
    public boolean isAnalysed(JournalEntry entry) {
        boolean comparable = entry.getContentEmbedding() != null
                ? entry.getContentEmbedding().size() == themeDimensions() && themeDimensions() > 0
                : Objects.equals(entry.getThemesVersion(), themesVersion) && themesVersion != null;
        boolean userTagged = entry.getThemeOverrides() != null && entry.getThemeOverrides().getAdded() != null
                && !entry.getThemeOverrides().getAdded().isEmpty();
        return comparable || userTagged;
    }

    /** Entries grouped by theme: each analysed entry counts towards each of its (effective) themes. */
    public record ThemeGroups(int analysedEntries, List<ThemeGroup> themes) {
    }

    /** One theme and the entries touching on it, in the order they were given. */
    public record ThemeGroup(String name, List<JournalEntry> entries) {
    }

    /**
     * The one place that decides which themes a set of entries is about: the insights page and the
     * AI's "the user often writes about" context both read it, so they can't disagree. Themes come
     * most common first (ties by name). An analysed entry may have no themes (an everyday entry).
     */
    public ThemeGroups groupByTheme(List<JournalEntry> entries) {
        Map<String, List<JournalEntry>> byTheme = new HashMap<>();
        int analysed = 0;
        for (JournalEntry entry : entries) {
            if (!isAnalysed(entry)) continue;
            analysed++;
            for (String theme : effectiveThemes(entry)) {
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
