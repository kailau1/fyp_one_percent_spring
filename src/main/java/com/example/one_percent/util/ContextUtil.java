package com.example.one_percent.util;

import com.example.one_percent.model.Habit;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextUtil {

    private static final Logger logger = LoggerFactory.getLogger(ContextUtil.class);
    private final HabitRepository habitRepository;
    private final JournalRepository journalRepository;
    private final EmbeddingService embeddingService;

    public ContextUtil(HabitRepository habitRepository, JournalRepository journalRepository, EmbeddingService embeddingService) {
        this.habitRepository = habitRepository;
        this.journalRepository = journalRepository;
        this.embeddingService = embeddingService;
    }

    public String getUserContext(String userId) {
        List<Habit> habits = habitRepository.findByUserId(userId);
        List<String> themes = embeddingService.getFrequentThemes(userId);

        StringBuilder context = new StringBuilder("Context for the assistant:\n");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        String habitDescriptions = habits.stream()
                // Only recently-active habits; stale ones used to be joined in as the text "null".
                .filter(habit -> habit.getLastUpdated() != null && habit.getLastUpdated().isAfter(cutoff))
                .map(habit -> "trigger-action".equalsIgnoreCase(habit.getHabitType())
                        ? "Trigger: " + habit.getTrigger() + " → Action: " + habit.getAction()
                        : habit.getHabitName())
                .collect(Collectors.joining(", "));

        if (!habitDescriptions.isEmpty()) {
            context.append("- User's active habits include:\n");
            context.append(habitDescriptions).append(".\n");
        }

        if (!themes.isEmpty()) {
            context.append("- The user often writes about: ");
            context.append(String.join(", ", themes)).append(".\n");
        }

        context.append("--- End of context ---");

        logger.debug("User Context Themes: {}", themes);

        return context.toString();
    }

    public String getLLMContext(JournalEntry currentEntry) {
        List<JournalEntry> pastEntries = journalRepository.findByUserId(currentEntry.getUserId());

        List<JournalEntry> validEntries = pastEntries.stream()
                .filter(p -> p.getContentEmbedding() != null && p.getLlmSummary() != null)
                .toList();

        List<JournalEntry> mostSimilarEntries = getTopNMatches(currentEntry, validEntries, 3);

        List<String> summaries = mostSimilarEntries.stream()
                .map(JournalEntry::getLlmSummary)
                .toList();

        if (summaries.isEmpty()) {
            summaries = pastEntries.stream()
                    .filter(p -> p.getLlmSummary() != null)
                    .sorted(Comparator.comparing(JournalEntry::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(3)
                    .map(JournalEntry::getLlmSummary)
                    .toList();
        }

        StringBuilder context = new StringBuilder();

        if (!summaries.isEmpty()) {
            context.append("- Avoid repeating the following previously given advice:\n");
            for (String summary : summaries) {
                context.append("  • ").append(summary).append("\n");
            }
        }

        logger.debug("Top Similar Summaries Used in Context: {}", summaries);

        return context.toString();
    }

    public List<JournalEntry> getTopNMatches(JournalEntry currentEntry, List<JournalEntry> candidates, int n) {
        // Entries saved while OpenAI was unavailable have no embedding to compare with.
        if (currentEntry.getContentEmbedding() == null) return List.of();

        return candidates.stream()
                .filter(p -> p.getContentEmbedding() != null)
                .sorted(Comparator.comparingDouble(
                        p -> -embeddingService.cosineSimilarity(currentEntry.getContentEmbedding(), p.getContentEmbedding()))
                )
                .limit(n)
                .toList();
    }
}