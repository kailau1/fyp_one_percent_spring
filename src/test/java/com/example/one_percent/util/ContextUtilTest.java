package com.example.one_percent.util;

import com.example.one_percent.model.Habit;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextUtilTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private JournalRepository journalRepository;
    @Mock
    private EmbeddingService embeddingService;

    private ContextUtil contextUtil;

    @BeforeEach
    void setUp() {
        contextUtil = new ContextUtil(habitRepository, journalRepository, embeddingService);
        // use the real maths so similarity ranking is meaningful
        lenient().when(embeddingService.cosineSimilarity(any(), any())).thenCallRealMethod();
    }

    private Habit habit(String name, String type, String trigger, String action, LocalDateTime lastUpdated) {
        return new Habit(null, "alice", name, null, "#fff", false, type, trigger, action, null, lastUpdated);
    }

    private JournalEntry entry(String summary, List<Double> embedding, LocalDateTime createdAt) {
        JournalEntry e = new JournalEntry();
        e.setUserId("alice");
        e.setLlmSummary(summary);
        e.setContentEmbedding(embedding);
        e.setCreatedAt(createdAt);
        return e;
    }

    // ---- getUserContext ----

    @Test
    void userContext_listsRecentHabitsAndFormatsTriggerActionHabits() {
        LocalDateTime recent = LocalDateTime.now().minusDays(1);
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(
                habit("Read", "standard", null, null, recent),
                habit(null, "trigger-action", "pour coffee", "meditate", recent)));
        when(embeddingService.getFrequentThemes("alice")).thenReturn(List.of());

        String ctx = contextUtil.getUserContext("alice");

        assertThat(ctx).contains("Read, Trigger: pour coffee → Action: meditate");
    }

    @Test
    void userContext_skipsStaleHabitsWithoutWritingNull() {
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(
                habit("Old habit", "standard", null, null, LocalDateTime.now().minusDays(30)),
                habit("Recent", "standard", null, null, LocalDateTime.now()),
                habit("Never updated", "standard", null, null, null)));
        when(embeddingService.getFrequentThemes("alice")).thenReturn(List.of());

        String ctx = contextUtil.getUserContext("alice");

        assertThat(ctx).contains("Recent").doesNotContain("Old habit", "Never updated", "null");
    }

    @Test
    void userContext_omitsHabitSectionWhenNothingIsRecent() {
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(
                habit("Old", "standard", null, null, LocalDateTime.now().minusDays(30))));
        when(embeddingService.getFrequentThemes("alice")).thenReturn(List.of("burnout"));

        String ctx = contextUtil.getUserContext("alice");

        assertThat(ctx).doesNotContain("active habits");
        assertThat(ctx).contains("The user often writes about: burnout.");
    }

    // ---- getTopNMatches / getLLMContext ----

    @Test
    void topNMatches_ranksBySimilarityToCurrentEntry() {
        JournalEntry current = entry(null, List.of(1.0, 0.0), null);
        JournalEntry close = entry("close", List.of(0.9, 0.1), null);
        JournalEntry middling = entry("middling", List.of(0.5, 0.5), null);
        JournalEntry far = entry("far", List.of(0.0, 1.0), null);
        JournalEntry noEmbedding = entry("none", null, null);

        List<JournalEntry> top = contextUtil.getTopNMatches(current, List.of(far, noEmbedding, middling, close), 2);

        assertThat(top).containsExactly(close, middling);
    }

    @Test
    void topNMatches_isEmptyWhenTheCurrentEntryHasNoEmbedding() {
        JournalEntry current = entry(null, null, null); // saved while OpenAI was unavailable
        JournalEntry past = entry("past", List.of(1.0, 0.0), null);

        assertThat(contextUtil.getTopNMatches(current, List.of(past), 3)).isEmpty();
    }

    @Test
    void llmContext_listsSummariesOfMostSimilarPastEntries() {
        JournalEntry current = entry(null, List.of(1.0, 0.0), null);
        when(journalRepository.findByUserId("alice")).thenReturn(List.of(
                entry("Try the 2-minute rule", List.of(0.9, 0.1), null),
                entry("Unrelated advice", List.of(0.0, 1.0), null),
                entry(null, List.of(1.0, 0.0), null)));  // no feedback yet: must be skipped

        String ctx = contextUtil.getLLMContext(current);

        assertThat(ctx).startsWith("- Avoid repeating the following previously given advice:")
                .contains("Try the 2-minute rule")
                .doesNotContain("null");
    }

    @Test
    void llmContext_fallsBackToMostRecentSummariesAndToleratesMissingDates() {
        JournalEntry current = entry(null, List.of(1.0), null);
        LocalDateTime now = LocalDateTime.now();
        when(journalRepository.findByUserId("alice")).thenReturn(List.of(
                entry("oldest", null, now.minusDays(5)),
                entry("undated", null, null),
                entry("newest", null, now),
                entry("middle", null, now.minusDays(1))));

        String ctx = contextUtil.getLLMContext(current);

        assertThat(ctx).contains("newest", "middle", "oldest").doesNotContain("undated");
        assertThat(ctx.indexOf("newest")).isLessThan(ctx.indexOf("middle"));
    }

    @Test
    void llmContext_isEmptyForAUsersFirstEntry() {
        when(journalRepository.findByUserId("alice")).thenReturn(List.of());

        assertThat(contextUtil.getLLMContext(entry(null, List.of(1.0), null))).isEmpty();
    }
}
