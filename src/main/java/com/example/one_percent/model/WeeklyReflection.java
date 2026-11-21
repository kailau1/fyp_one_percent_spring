package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A user's week read back to them, written once (the first time they open it) and kept. Keyed by
 * user and week, so creating it is insert-only: two tabs opening it at once can't make two.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "weekly_reflections")
public class WeeklyReflection {

    /** userId + ":" + weekStart, e.g. "6735…:2026-11-09". */
    @Id
    private String id;
    private String userId;
    /** The Monday the week starts on, in the user's time zone. */
    private LocalDate weekStart;
    private List<String> entryIds;
    private int entryCount;
    /** Distinct days with at least one entry. */
    private int daysWritten;
    /** The week's themes, most common first. */
    private List<ThemeCount> themes;
    private List<HabitDays> habits;
    /** Two or three sentences on the week, written by the AI. */
    private String opening;
    /** The user's own words, each checked to appear in an entry. */
    private List<Quote> quotes;
    /** One open question to carry into next week. */
    private String question;
    /** The model that wrote it. */
    private String model;
    private Instant createdAt;

    public static String idFor(String userId, LocalDate weekStart) {
        return userId + ":" + weekStart;
    }

    /** `thisWeek` entries about a theme, against `previousFourWeeks` in the four weeks before. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeCount {
        private String name;
        private int thisWeek;
        private int previousFourWeeks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HabitDays {
        private String name;
        private String colour;
        /** Days ticked, out of the week's 7. */
        private int daysDone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quote {
        private String entryId;
        private String entryTitle;
        private String text;
    }
}
