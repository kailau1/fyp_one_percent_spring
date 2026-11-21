package com.example.one_percent.service;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.config.AiSettings;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.insights.InsightsService;
import com.example.one_percent.support.TestAuth;
import com.example.one_percent.util.UserClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock
    private JournalRepository journalRepository;
    private InsightsService insightsService;
    private EmbeddingService embeddingService;
    // "now" for these tests: Friday 20 November 2026, midday UTC
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);

    // one-hot theme vectors, so each entry's three closest themes are easy to read off its embedding
    private static final List<Double> A = List.of(3.0, 2.0, 1.0, 0.0); // burnout, anxiety, discipline
    private static final List<Double> B = List.of(0.0, 2.0, 1.0, 3.0); // gratitude, anxiety, discipline

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // the real theme matching, so this tests what the page will actually show
        embeddingService = new EmbeddingService(journalRepository,
                new OpenAIClient(AiSettings.openAiDefaults("test-key", "http://ai.invalid/v1")));
        ((Map<String, List<Double>>) ReflectionTestUtils.getField(embeddingService, "themeEmbeddings")).putAll(Map.of(
                "burnout", List.of(1.0, 0.0, 0.0, 0.0),
                "anxiety", List.of(0.0, 1.0, 0.0, 0.0),
                "discipline", List.of(0.0, 0.0, 1.0, 0.0),
                "gratitude", List.of(0.0, 0.0, 0.0, 1.0)));
        insightsService = new InsightsService(journalRepository, embeddingService, new UserClock(NOW));
        TestAuth.loginAs("alice");
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static JournalEntry entry(String id, List<Double> embedding, String createdAt) {
        JournalEntry entry = entry(id, embedding);
        entry.setCreatedAt(Instant.parse(createdAt));
        return entry;
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

    @Test
    void countsEachThemeByMonthForTheLastSixMonths() {
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(
                entry("nov", A, "2026-11-14T09:00:00Z"),
                entry("oct1", A, "2026-10-03T09:00:00Z"),
                entry("oct2", B, "2026-10-20T09:00:00Z"),
                entry("jun", B, "2026-06-01T09:00:00Z"),
                entry("may", A, "2026-05-31T09:00:00Z"), // before the window: counted in all, not by month
                entry("unanalysed", null, "2026-11-01T09:00:00Z")));

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getMonths()).containsExactly("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");
        assertThat(insights.getEntriesPerMonth()).containsExactly(1, 0, 0, 0, 2, 1);
        ThemeInsightsDTO.Theme burnout = theme(insights, "burnout");
        assertThat(burnout.getEntryCount()).isEqualTo(3);
        assertThat(burnout.getMonthlyCounts()).containsExactly(0, 0, 0, 0, 1, 1);
        assertThat(theme(insights, "gratitude").getMonthlyCounts()).containsExactly(1, 0, 0, 0, 1, 0);
    }

    @Test
    void monthsFollowTheUsersTimeZone() {
        TestAuth.clear();
        var user = TestAuth.user("alice");
        user.setTimeZone("America/New_York");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, List.of()));
        // 03:30 UTC on 1 November is still 31 October, 23:30, in New York
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(entry("late", A, "2026-11-01T03:30:00Z")));

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getEntriesPerMonth()).containsExactly(0, 0, 0, 0, 1, 0);
        assertThat(theme(insights, "burnout").getMonthlyCounts()).containsExactly(0, 0, 0, 0, 1, 0);
    }

    @Test
    void monthlyCountsRespectTheUsersCorrections() {
        JournalEntry corrected = entry("e1", A, "2026-11-10T09:00:00Z");
        JournalEntry.ThemeOverrides overrides = new JournalEntry.ThemeOverrides();
        overrides.setRemoved(List.of("burnout"));
        overrides.setAdded(List.of("gratitude"));
        corrected.setThemeOverrides(overrides);
        when(journalRepository.findForThemes("alice")).thenReturn(List.of(corrected));

        ThemeInsightsDTO insights = insightsService.getThemeInsights();

        assertThat(insights.getThemes()).extracting(ThemeInsightsDTO.Theme::getName).doesNotContain("burnout");
        assertThat(theme(insights, "gratitude").getMonthlyCounts()).containsExactly(0, 0, 0, 0, 0, 1);
    }

    private static ThemeInsightsDTO.Theme theme(ThemeInsightsDTO insights, String name) {
        return insights.getThemes().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
