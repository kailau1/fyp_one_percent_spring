package com.example.one_percent.service;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.config.AiSettings;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.embedding.ThemeDefinitions;
import com.example.one_percent.service.embedding.ThemeVectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/** Fast, offline tests for the similarity + theme-detection logic (no OpenAI calls). */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private JournalRepository journalRepository;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(journalRepository, new OpenAIClient(AiSettings.openAiDefaults("test-key", "http://ai.invalid/v1")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Double>> themes() {
        return (Map<String, List<Double>>) ReflectionTestUtils.getField(embeddingService, "themeEmbeddings");
    }

    private JournalEntry entryWithEmbedding(List<Double> embedding) {
        JournalEntry e = new JournalEntry();
        e.setContentEmbedding(embedding);
        return e;
    }

    @Test
    void cosineSimilarity_identicalVectorsIsOne() {
        assertThat(embeddingService.cosineSimilarity(List.of(1.0, 2.0, 3.0), List.of(1.0, 2.0, 3.0)))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_ignoresMagnitude() {
        assertThat(embeddingService.cosineSimilarity(List.of(1.0, 1.0), List.of(5.0, 5.0)))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_orthogonalVectorsIsZero() {
        assertThat(embeddingService.cosineSimilarity(List.of(1.0, 0.0), List.of(0.0, 1.0)))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_oppositeVectorsIsMinusOne() {
        assertThat(embeddingService.cosineSimilarity(List.of(1.0, 2.0), List.of(-1.0, -2.0)))
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_returnsZeroForMismatchedLengthsOrZeroVectors() {
        assertThat(embeddingService.cosineSimilarity(List.of(1.0, 2.0), List.of(1.0))).isZero();
        assertThat(embeddingService.cosineSimilarity(List.of(0.0, 0.0), List.of(1.0, 1.0))).isZero();
    }

    @Test
    void themesFileIsChosenByEmbeddingModel() {
        assertThat(EmbeddingService.themesResourceFor("text-embedding-3-small", null)).isEqualTo("themes/text-embedding-3-small.json");
        // a model's shortened vectors can't be compared with its full-length ones, so the size is part of the name
        assertThat(EmbeddingService.themesResourceFor("gemini-embedding-2", 768)).isEqualTo("themes/gemini-embedding-2-768.json");
        // anything odd in a model name can't escape the themes folder
        assertThat(EmbeddingService.themesResourceFor("models/../../secret", null)).isEqualTo("themes/models_.._.._secret.json");
    }

    @Test
    void noThemesForAModelWithoutAThemesFile() {
        EmbeddingService other = new EmbeddingService(journalRepository,
                new OpenAIClient(new AiSettings("k", "http://ai.invalid/v1", "s", "f", "no-such-embedding-model", null, null, null)));

        other.loadThemeEmbeddings();

        assertThat(other.themeDimensions()).isZero();
        assertThat(other.tagThemes(List.of(1.0, 0.0))).isEmpty();
    }

    @Test
    void loadThemeEmbeddings_loadsTheBundledThemesFile() {
        embeddingService.loadThemeEmbeddings();

        assertThat(themes()).hasSize(10);
        int dimensions = themes().values().iterator().next().size();
        assertThat(themes().values()).allSatisfy(v -> assertThat(v).hasSize(dimensions));
    }

    @Test
    void getFrequentThemes_returnsTopThreeMostCommonAcrossEntries() {
        themes().putAll(Map.of(
                "procrastination", List.of(1.0, 0.0, 0.0, 0.0),
                "self-doubt", List.of(0.0, 1.0, 0.0, 0.0),
                "gratitude", List.of(0.0, 0.0, 1.0, 0.0),
                "burnout", List.of(0.0, 0.0, 0.0, 1.0)));

        when(journalRepository.findForThemes("alice")).thenReturn(List.of(
                entryWithEmbedding(List.of(1.0, 0.6, 0.3, 0.0)),  // procrastination, self-doubt, gratitude
                entryWithEmbedding(List.of(0.9, 0.5, 0.0, 0.2)),  // procrastination, self-doubt, burnout
                entryWithEmbedding(List.of(0.8, 0.4, 0.2, 0.0)),  // procrastination, self-doubt, gratitude
                entryWithEmbedding(null)));                       // skipped, no embedding

        List<String> result = embeddingService.getFrequentThemes("alice");

        assertThat(result).containsExactlyInAnyOrder("procrastination", "self-doubt", "gratitude");
    }

    @Test
    void legacyVectors_tagTheThreeClosestThemesBestFirst() {
        themes().putAll(Map.of(
                "procrastination", List.of(1.0, 0.0, 0.0, 0.0),
                "self-doubt", List.of(0.0, 1.0, 0.0, 0.0),
                "gratitude", List.of(0.0, 0.0, 1.0, 0.0),
                "burnout", List.of(0.0, 0.0, 0.0, 1.0)));

        assertThat(embeddingService.tagThemes(List.of(0.2, 0.9, 0.5, 0.1)).keySet())
                .containsExactly("self-doubt", "gratitude", "procrastination");
    }

    @Test
    void tagThemes_skipsEmbeddingsThatCantBeCompared() {
        themes().put("procrastination", List.of(1.0, 0.0, 0.0));

        // a different length means a different model (e.g. a local stand-in): no themes, not three random ones
        assertThat(embeddingService.tagThemes(List.of(1.0, 0.0))).isEmpty();
        assertThat(embeddingService.tagThemes(null)).isEmpty();
        assertThat(embeddingService.tagThemes(List.of())).isEmpty();
    }

    // ---- calibrated vectors: themes only when an entry is clearly about them ----

    /** Three one-hot themes; an entry's score with each is its cosine with that axis. */
    private void useCalibrated(double minSimilarity, double secondaryMargin) {
        embeddingService.useVectors(new ThemeVectors("m", 3, embeddingService.definitions().hash(), minSimilarity, secondaryMargin,
                Map.of("anxiety", List.of(1.0, 0.0, 0.0), "burnout", List.of(0.0, 1.0, 0.0), "gratitude", List.of(0.0, 0.0, 1.0))), "test");
    }

    @Test
    void calibrated_anEverydayEntryGetsNoTheme() {
        useCalibrated(0.8, 0.05);

        // equally (weakly) like everything: cos = 0.577 with each, below 0.8
        assertThat(embeddingService.tagThemes(List.of(1.0, 1.0, 1.0))).isEmpty();
    }

    @Test
    void calibrated_aClearEntryGetsJustItsTheme() {
        useCalibrated(0.8, 0.05);

        assertThat(embeddingService.tagThemes(List.of(0.95, 0.3, 0.0)).keySet()).containsExactly("anxiety");
    }

    @Test
    void calibrated_aSecondThemeCountsOnlyWhenNearlyAsStrong() {
        useCalibrated(0.6, 0.05);

        // anxiety 0.707, burnout 0.700: both clear and within the margin, best first
        var both = embeddingService.tagThemes(List.of(1.01, 1.0, 0.0));
        assertThat(both.keySet()).containsExactly("anxiety", "burnout");
        assertThat(both.get("anxiety")).isGreaterThan(both.get("burnout"));
        // burnout well behind: only anxiety
        assertThat(embeddingService.tagThemes(List.of(1.0, 0.7, 0.0)).keySet()).containsExactly("anxiety");
    }

    @Test
    void calibrated_neverMoreThanThreeThemes() {
        embeddingService.useVectors(new ThemeVectors("m", 2, "h", 0.0, 1.0, Map.of(
                "a", List.of(1.0, 0.0), "b", List.of(0.9, 0.1), "c", List.of(0.8, 0.2), "d", List.of(0.7, 0.3))), "test");

        assertThat(embeddingService.tagThemes(List.of(1.0, 0.1))).hasSize(EmbeddingService.MAX_THEMES_PER_ENTRY);
    }

    // ---- stored tags and the user's corrections ----

    private JournalEntry taggedEntry(List<Double> embedding) {
        JournalEntry entry = entryWithEmbedding(embedding);
        embeddingService.tag(entry);
        return entry;
    }

    @Test
    void tag_storesScoresAndWhichVectorsMadeThem() {
        useCalibrated(0.8, 0.05);

        JournalEntry entry = taggedEntry(List.of(0.95, 0.3, 0.0));

        assertThat(entry.getThemeScores()).containsOnlyKeys("anxiety");
        assertThat(entry.getThemesVersion()).isEqualTo(embeddingService.themesVersion());
    }

    @Test
    void storedTagsAreUsedWhileCurrentAndWorkedOutAgainWhenStale() {
        useCalibrated(0.8, 0.05);
        JournalEntry entry = taggedEntry(List.of(0.95, 0.3, 0.0));
        entry.setThemeScores(new java.util.LinkedHashMap<>(Map.of("gratitude", 0.9))); // pretend: stored earlier

        assertThat(embeddingService.effectiveThemes(entry)).containsExactly("gratitude"); // current: stored wins

        entry.setThemesVersion("older-vectors:abc");
        assertThat(embeddingService.effectiveThemes(entry)).containsExactly("anxiety"); // stale: recomputed
    }

    @Test
    void effectiveThemes_applyTheUsersCorrections() {
        useCalibrated(0.6, 0.05);
        JournalEntry entry = taggedEntry(List.of(1.01, 1.0, 0.0)); // anxiety, burnout
        entry.setThemeOverrides(new JournalEntry.ThemeOverrides(List.of("gratitude"), List.of("burnout")));

        assertThat(embeddingService.effectiveThemes(entry)).containsExactly("anxiety", "gratitude");
    }

    @Test
    void groupByTheme_countsEverydayEntriesAsAnalysedWithNoThemes() {
        useCalibrated(0.8, 0.05);
        JournalEntry clear = taggedEntry(List.of(0.95, 0.3, 0.0));
        JournalEntry everyday = taggedEntry(List.of(1.0, 1.0, 1.0));
        JournalEntry otherModel = taggedEntry(List.of(1.0, 0.0)); // can't be compared: not analysed
        JournalEntry userTagged = entryWithEmbedding(null);
        userTagged.setThemeOverrides(new JournalEntry.ThemeOverrides(List.of("gratitude"), List.of()));

        EmbeddingService.ThemeGroups groups = embeddingService.groupByTheme(List.of(clear, everyday, otherModel, userTagged));

        assertThat(groups.analysedEntries()).isEqualTo(3);
        assertThat(groups.themes()).extracting(EmbeddingService.ThemeGroup::name).containsExactly("anxiety", "gratitude");
    }

    @Test
    void definitionsShipWithEnoughVariedExamples() {
        ThemeDefinitions definitions = embeddingService.definitions();

        assertThat(definitions.names()).hasSize(10);
        assertThat(definitions.themes()).allSatisfy(theme -> {
            assertThat(theme.meaning()).isNotBlank();
            assertThat(theme.examples()).hasSizeGreaterThanOrEqualTo(8).doesNotHaveDuplicates();
        });
        assertThat(definitions.everyday()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(definitions.hash()).hasSize(12).isEqualTo(ThemeDefinitions.load().hash());
    }

    @Test
    void vectorsFilesInBothFormatsAreRead() throws Exception {
        var legacy = ThemeVectors.read(new java.io.ByteArrayInputStream("{\"a\":[1.0,0.0]}".getBytes()));
        var calibrated = ThemeVectors.read(new java.io.ByteArrayInputStream(
                "{\"model\":\"m\",\"minSimilarity\":0.76,\"secondaryMargin\":0.02,\"themes\":{\"a\":[1.0,0.0]}}".getBytes()));

        assertThat(legacy.isLegacy()).isTrue();
        assertThat(legacy.themes()).containsKey("a");
        assertThat(calibrated.isLegacy()).isFalse();
        assertThat(calibrated.minSimilarity()).isEqualTo(0.76);
    }

    @Test
    void consistentThemes_dropsVectorsOfTheOddLengthOut() {
        Map<String, List<Double>> kept = EmbeddingService.consistentThemes(Map.of(
                "a", List.of(1.0, 0.0), "b", List.of(0.0, 1.0), "c", List.of(1.0, 1.0), "odd", List.of(1.0, 0.0, 0.0)));

        assertThat(kept).containsOnlyKeys("a", "b", "c");
    }

    @Test
    void getFrequentThemes_isEmptyWhenUserHasNoEmbeddedEntries() {
        themes().put("procrastination", List.of(1.0));
        when(journalRepository.findForThemes("alice")).thenReturn(new ArrayList<>(List.of(entryWithEmbedding(null))));

        assertThat(embeddingService.getFrequentThemes("alice")).isEmpty();
    }
}
