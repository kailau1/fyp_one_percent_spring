package com.example.one_percent.service.insights;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.security.CurrentUser;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightsService {

    /** How many example entries each theme links to. */
    static final int RECENT_ENTRIES_PER_THEME = 3;
    /** How many calendar months the trend covers, including the current one. */
    static final int TREND_MONTHS = 6;

    private final JournalRepository journalRepository;
    private final EmbeddingService embeddingService;
    private final UserClock userClock;

    /**
     * Which themes the user's entries touch on, how often (in all, and in each of the last
     * TREND_MONTHS months of the user's calendar), and their newest examples. Themes are read from
     * each entry's stored tags and the user's corrections; no AI calls.
     */
    public ThemeInsightsDTO getThemeInsights() {
        User user = CurrentUser.get();
        List<JournalEntry> entries = journalRepository.findForThemes(user.getId()); // newest first
        EmbeddingService.ThemeGroups groups = embeddingService.groupByTheme(entries);
        Months months = Months.endingAt(userClock.today(user), userClock.zoneOf(user));

        List<ThemeInsightsDTO.Theme> themes = groups.themes().stream()
                .map(group -> new ThemeInsightsDTO.Theme(group.name(), group.entries().size(), group.entries().stream()
                        .limit(RECENT_ENTRIES_PER_THEME)
                        .map(entry -> new ThemeInsightsDTO.EntryRef(entry.getId(), entry.getTitle(), entry.getCreatedAt()))
                        .toList(), months.count(group.entries())))
                .toList();
        List<JournalEntry> analysed = entries.stream().filter(embeddingService::isAnalysed).toList();
        return new ThemeInsightsDTO(entries.size(), groups.analysedEntries(), themes, months.labels(), months.count(analysed));
    }

    /** The last TREND_MONTHS calendar months up to `today`, counted in the user's time zone. */
    record Months(List<YearMonth> months, ZoneId zone) {

        static Months endingAt(LocalDate today, ZoneId zone) {
            YearMonth current = YearMonth.from(today);
            List<YearMonth> months = new ArrayList<>();
            for (int back = TREND_MONTHS - 1; back >= 0; back--) months.add(current.minusMonths(back));
            return new Months(months, zone);
        }

        List<String> labels() {
            return months.stream().map(YearMonth::toString).toList();
        }

        /** How many of `entries` were written in each month; older or undated entries aren't counted. */
        List<Integer> count(List<JournalEntry> entries) {
            List<Integer> counts = new ArrayList<>(Collections.nCopies(months.size(), 0));
            for (JournalEntry entry : entries) {
                Instant written = entry.getCreatedAt();
                if (written == null) continue;
                int i = months.indexOf(YearMonth.from(written.atZone(zone)));
                if (i >= 0) counts.set(i, counts.get(i) + 1);
            }
            return counts;
        }
    }
}
