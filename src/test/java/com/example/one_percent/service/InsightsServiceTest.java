package com.example.one_percent.service;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.config.AiSettings;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.insights.InsightsService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock
    private JournalRepository journalRepository;
    private InsightsService insightsService;

    // one-hot theme vectors, so each entry's three closest themes are easy to read off its embedding
    private static final List<Double> A = List.of(3.0, 2.0, 1.0, 0.0); // burnout, anxiety, discipline
    private static final List<Double> B = List.of(0.0, 2.0, 1.0, 3.0); // gratitude, anxiety, discipline

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // the real theme matching, so this tests what the page will actually show
        EmbeddingService embeddingService = new EmbeddingService(journalRepository,
                new OpenAIClient(AiSettings.openAiDefaults("test-key", "http://ai.invalid/v1")));
        ((Map<String, List<Double>>) ReflectionTestUtils.getField(embeddingService, "themeEmbeddings")).putAll(Map.of(
                "burnout", List.of(1.0, 0.0, 0.0, 0.0),
                "anxiety", List.of(0.0, 1.0, 0.0, 0.0),
                "discipline", List.of(0.0, 0.0, 1.0, 0.0),
                "gratitude", List.of(0.0, 0.0, 0.0, 1.0)));
        insightsService = new InsightsService(journalRepository, embeddingService);
        TestAuth.loginAs("alice");
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static JournalEntry entry(String id, List<Double> embedding) {
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setTitle("Title " + id);
        entry.setCreatedAt(Instant.parse("2026-11-14T09:00:00Z"));
        entry.setContentEmbedding(embedding);
        return entry;
    }

    @Test
    void countsThemesAcrossTheUsersEntriesMostCommonFirst() {
        // newest first, as the repository returns them; e1 was embedded by another model, e0 not at all
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(
                entry("e5", A), entry("e4", B), entry("e3", A), entry("e2", A), entry("e1", List.of(1.0, 0.0)), entry("e0", null)));

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getTotalEntries()).isEqualTo(6);
        assertThat(insights.getAnalysedEntries()).isEqualTo(4);
        assertThat(insights.getThemes()).extracting(ThemeInsightsDTO.Theme::getName, ThemeInsightsDTO.Theme::getEntryCount)
                .containsExactly(
                        tuple("anxiety", 4),
                        tuple("discipline", 4),
                        tuple("burnout", 3),
                        tuple("gratitude", 1));
    }

    @Test
    void linksEachThemeToItsThreeNewestEntries() {
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(entry("e4", A), entry("e3", A), entry("e2", A), entry("e1", A)));

        ThemeInsightsDTO.Theme burnout = insightsService.getThemeInsights().getThemes().stream()
                .filter(theme -> theme.getName().equals("burnout")).findFirst().orElseThrow();

        assertThat(burnout.getEntryCount()).isEqualTo(4);
        assertThat(burnout.getRecentEntries()).extracting(ThemeInsightsDTO.EntryRef::getId).containsExactly("e4", "e3", "e2");
        assertThat(burnout.getRecentEntries().get(0).getTitle()).isEqualTo("Title e4");
    }

    @Test
    void onlyReadsTheLoggedInUsersEntries() {
        when(journalRepository.findForThemes("alice")).thenReturn(List.of());

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getTotalEntries()).isZero();
        assertThat(insights.getThemes()).isEmpty();
        verify(journalRepository).findForThemes("alice");
        verifyNoMoreInteractions(journalRepository);
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
