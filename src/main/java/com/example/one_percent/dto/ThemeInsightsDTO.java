package com.example.one_percent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** GET /api/insights/themes: what the user's journal keeps coming back to. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeInsightsDTO {
    /** All of the user's entries. */
    private int totalEntries;
    /** Entries that could be matched to themes (the rest were saved while AI analysis was unavailable). */
    private int analysedEntries;
    /** Themes that at least one entry touches on, most common first. */
    private List<Theme> themes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Theme {
        /** e.g. "procrastination" */
        private String name;
        /** How many analysed entries are about it (an entry can have up to three themes, or none). */
        private int entryCount;
        /** Up to three of those entries, newest first, to read again. */
        private List<EntryRef> recentEntries;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryRef {
        private String id;
        private String title;
        private Instant createdAt;
    }
}
