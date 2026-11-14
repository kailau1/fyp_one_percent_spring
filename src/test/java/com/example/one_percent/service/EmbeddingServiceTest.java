package com.example.one_percent.service;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
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
        embeddingService = new EmbeddingService(journalRepository);
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

        when(journalRepository.findByUserId("alice")).thenReturn(List.of(
                entryWithEmbedding(List.of(1.0, 0.6, 0.3, 0.0)),  // procrastination, self-doubt, gratitude
                entryWithEmbedding(List.of(0.9, 0.5, 0.0, 0.2)),  // procrastination, self-doubt, burnout
                entryWithEmbedding(List.of(0.8, 0.4, 0.2, 0.0)),  // procrastination, self-doubt, gratitude
                entryWithEmbedding(null)));                       // skipped, no embedding

        List<String> result = embeddingService.getFrequentThemes("alice");

        assertThat(result).containsExactlyInAnyOrder("procrastination", "self-doubt", "gratitude");
    }

    @Test
    void getFrequentThemes_isEmptyWhenUserHasNoEmbeddedEntries() {
        themes().put("procrastination", List.of(1.0));
        when(journalRepository.findByUserId("alice")).thenReturn(new ArrayList<>(List.of(entryWithEmbedding(null))));

        assertThat(embeddingService.getFrequentThemes("alice")).isEmpty();
    }
}
