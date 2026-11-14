package com.example.one_percent.service;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.insights.InsightsService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock
    private JournalRepository journalRepository;
    @Mock
    private EmbeddingService embeddingService;

    private InsightsService insightsService;

    @BeforeEach
    void setUp() {
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
        List<Double> a = List.of(1.0), b = List.of(2.0), c = List.of(3.0);
        // newest first, as the repository returns them
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(
                entry("e5", a), entry("e4", b), entry("e3", a), entry("e2", a), entry("e1", c), entry("e0", null)));
        when(embeddingService.closestThemes(a, 3)).thenReturn(List.of("burnout", "anxiety", "discipline"));
        when(embeddingService.closestThemes(b, 3)).thenReturn(List.of("gratitude", "anxiety", "confidence"));
        when(embeddingService.closestThemes(c, 3)).thenReturn(List.of()); // couldn't be analysed
        when(embeddingService.closestThemes(null, 3)).thenReturn(List.of());

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getTotalEntries()).isEqualTo(6);
        assertThat(insights.getAnalysedEntries()).isEqualTo(4);
        assertThat(insights.getThemes()).extracting(ThemeInsightsDTO.Theme::getName, ThemeInsightsDTO.Theme::getEntryCount)
                .containsExactly(
                        tuple("anxiety", 4),
                        tuple("burnout", 3),
                        tuple("discipline", 3),
                        tuple("confidence", 1),
                        tuple("gratitude", 1));
    }

    @Test
    void linksEachThemeToItsThreeNewestEntries() {
        List<Double> a = List.of(1.0);
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(entry("e4", a), entry("e3", a), entry("e2", a), entry("e1", a)));
        when(embeddingService.closestThemes(a, 3)).thenReturn(List.of("burnout"));

        ThemeInsightsDTO.Theme burnout = insightsService.getThemeInsights().getThemes().get(0);

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
