package com.example.one_percent.service.insights;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.security.CurrentUser;
import com.example.one_percent.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightsService {

    /** How many example entries each theme links to. */
    static final int RECENT_ENTRIES_PER_THEME = 3;

    private final JournalRepository journalRepository;
    private final EmbeddingService embeddingService;

    /**
     * Which themes the user's entries touch on, how often, and their newest examples. Reads every
     * entry's embedding (not its text) on each call: fine for a personal journal; if journals grow
     * into the thousands, store each entry's themes when it's saved instead.
     */
    public ThemeInsightsDTO getThemeInsights() {
        User user = CurrentUser.get();
        List<JournalEntry> entries = journalRepository.findForThemes(user.getId()); // newest first
        EmbeddingService.ThemeGroups groups = embeddingService.groupByTheme(entries);

        List<ThemeInsightsDTO.Theme> themes = groups.themes().stream()
                .map(group -> new ThemeInsightsDTO.Theme(group.name(), group.entries().size(), group.entries().stream()
                        .limit(RECENT_ENTRIES_PER_THEME)
                        .map(entry -> new ThemeInsightsDTO.EntryRef(entry.getId(), entry.getTitle(), entry.getCreatedAt()))
                        .toList()))
                .toList();
        return new ThemeInsightsDTO(entries.size(), groups.analysedEntries(), themes);
    }
}
