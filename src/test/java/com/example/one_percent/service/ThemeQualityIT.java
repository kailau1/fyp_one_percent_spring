package com.example.one_percent.service;

import com.example.one_percent.config.AiSettings;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How well theme detection works on labelled entries it has never seen, using the configured AI
 * service for real (src/test/resources/theme-quality). Opt-in, since it calls the API:
 * RUN_INTEGRATION_TESTS=true mvn -Dtest=ThemeQualityIT test
 *
 * Reads the AI settings like the app does: application.properties, plus the profile it names.
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class ThemeQualityIT {

    record Labelled(List<String> labels, String text) {
    }

    private static OpenAIClient client;
    private static EmbeddingService embeddingService;

    @BeforeAll
    static void connect() throws IOException {
        Properties props = new Properties();
        Path resources = Path.of("src/main/resources");
        try (InputStream is = new FileInputStream(resources.resolve("application.properties").toFile())) {
            props.load(is);
        }
        String profile = props.getProperty("spring.profiles.active", "");
        if (!profile.isBlank()) {
            try (InputStream is = new FileInputStream(resources.resolve("application-" + profile.trim() + ".properties").toFile())) {
                props.load(is);
            }
        }
        String dims = props.getProperty("ai.embedding-dimensions", "");
        client = new OpenAIClient(new AiSettings(
                props.getProperty("ai.api-key", props.getProperty("openai.api.key")),
                props.getProperty("ai.base-url", "https://api.openai.com/v1"),
                "unused", "unused",
                props.getProperty("ai.embedding-model", "text-embedding-3-small"),
                dims.isBlank() ? null : Integer.valueOf(dims.trim()), null, null));
        embeddingService = new EmbeddingService(Mockito.mock(JournalRepository.class), client);
        embeddingService.loadThemeEmbeddings();
        assertThat(embeddingService.themeDimensions()).as("theme vectors for this model (run generate-themes)").isPositive();
    }

    private static List<Labelled> load(String name) throws IOException {
        try (InputStream is = ThemeQualityIT.class.getResourceAsStream("/theme-quality/" + name)) {
            return new ObjectMapper().readValue(is, new TypeReference<>() {
            });
        }
    }

    /** Fraction of single-theme entries whose primary (first) tag is the right theme. */
    private static double primaryAccuracy(List<Labelled> set, List<List<String>> tags) {
        int right = 0, total = 0;
        for (int i = 0; i < set.size(); i++) {
            if (set.get(i).labels().size() != 1) continue;
            total++;
            if (!tags.get(i).isEmpty() && tags.get(i).get(0).equals(set.get(i).labels().get(0))) right++;
        }
        return (double) right / total;
    }

    /** Embeds in small batches with pauses: free tiers count each text against a per-minute limit. */
    private static List<List<String>> tag(List<Labelled> set) throws IOException, InterruptedException {
        List<String> texts = set.stream().map(Labelled::text).toList();
        List<List<Double>> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += 20) {
            if (start > 0) Thread.sleep(15_000);
            vectors.addAll(client.embedAll(texts.subList(start, Math.min(start + 20, texts.size()))));
        }
        List<List<String>> tags = new ArrayList<>();
        for (List<Double> v : vectors) tags.add(List.copyOf(embeddingService.tagThemes(v).keySet()));
        return tags;
    }

    private static void report(String name, List<Labelled> set, List<List<String>> tags) {
        System.out.printf("%n== %s: primary theme right for %.0f%% of single-theme entries%n", name, 100 * primaryAccuracy(set, tags));
        for (int i = 0; i < set.size(); i++) {
            boolean ok = set.get(i).labels().isEmpty() ? tags.get(i).isEmpty()
                    : !tags.get(i).isEmpty() && set.get(i).labels().contains(tags.get(i).get(0));
            System.out.printf("%s expected %-35s got %-45s %s%n", ok ? "  " : "XX", set.get(i).labels(), tags.get(i),
                    set.get(i).text().substring(0, Math.min(60, set.get(i).text().length())));
        }
    }

    @Test
    void directEntries() throws Exception {
        List<Labelled> set = load("direct.json");
        List<List<String>> tags = tag(set);
        report("direct", set, tags);

        assertThat(primaryAccuracy(set, tags)).isGreaterThanOrEqualTo(0.9);
        for (int i = 0; i < set.size(); i++) {
            if (set.get(i).labels().isEmpty()) {
                assertThat(tags.get(i)).as("everyday entry: %s", set.get(i).text()).isEmpty();
            }
            if (set.get(i).labels().size() == 2) {
                // two-theme entries: the primary tag must be one of them
                assertThat(tags.get(i)).as("two-theme entry: %s", set.get(i).text()).isNotEmpty();
                assertThat(set.get(i).labels()).contains(tags.get(i).get(0));
            }
        }
    }

    @Test
    void impliedEntries() throws Exception {
        List<Labelled> set = load("implied.json");
        List<List<String>> tags = tag(set);
        report("implied", set, tags);

        assertThat(primaryAccuracy(set, tags)).isGreaterThanOrEqualTo(0.9);
        Map<String, Long> untagged = Map.of("untagged", tags.stream().filter(List::isEmpty).count());
        assertThat(untagged.get("untagged")).as("implied entries left with no theme").isLessThanOrEqualTo(2);
    }
}
