package com.example.one_percent.service.insights;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsightsService {

    /** How many example entries each theme links to. */
    static final int RECENT_ENTRIES_PER_THEME = 3;

    private final JournalRepository journalRepository;
    private final EmbeddingService embeddingService;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user");
    }

    /**
     * Matches each of the user's entries to its closest themes (by embedding) and counts how often
     * each theme comes up. Entries without a usable embedding are counted but not analysed.
     */
    public ThemeInsightsDTO getThemeInsights() {
        User user = getAuthenticatedUser();
        List<JournalEntry> entries = journalRepository.findForThemes(user.getId()); // newest first

        Map<String, ThemeInsightsDTO.Theme> byName = new LinkedHashMap<>();
        int analysed = 0;
        for (JournalEntry entry : entries) {
            List<String> themes = embeddingService.closestThemes(entry.getContentEmbedding(), EmbeddingService.THEMES_PER_ENTRY);
            if (themes.isEmpty()) continue;
            analysed++;
            for (String name : themes) {
                ThemeInsightsDTO.Theme theme = byName.computeIfAbsent(name, n -> new ThemeInsightsDTO.Theme(n, 0, new ArrayList<>()));
                theme.setEntryCount(theme.getEntryCount() + 1);
                if (theme.getRecentEntries().size() < RECENT_ENTRIES_PER_THEME) {
                    theme.getRecentEntries().add(new ThemeInsightsDTO.EntryRef(entry.getId(), entry.getTitle(), entry.getCreatedAt()));
                }
            }
        }

        List<ThemeInsightsDTO.Theme> themes = byName.values().stream()
                .sorted(Comparator.comparingInt(ThemeInsightsDTO.Theme::getEntryCount).reversed()
                        .thenComparing(ThemeInsightsDTO.Theme::getName))
                .toList();
        return new ThemeInsightsDTO(entries.size(), analysed, themes);
    }
}
