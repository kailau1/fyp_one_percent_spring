package com.example.one_percent.dto;

import com.example.one_percent.model.WeeklyReflection;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A week read back to the user. `status` is "ready" (everything below is filled in) or
 * "notEnoughEntries" (only weekStart, weekEnd and entryCount: fewer than 2 entries that week).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeeklyReflectionDTO {
    public static final String READY = "ready";
    public static final String NOT_ENOUGH_ENTRIES = "notEnoughEntries";

    private String status;
    /** Monday, in the user's time zone. */
    private LocalDate weekStart;
    /** The Sunday it ends on. */
    private LocalDate weekEnd;
    private int entryCount;
    private Integer daysWritten;
    private List<WeeklyReflection.ThemeCount> themes;
    private List<WeeklyReflection.HabitDays> habits;
    private String opening;
    private List<WeeklyReflection.Quote> quotes;
    private String question;
    private Instant createdAt;

    public static WeeklyReflectionDTO notEnoughEntries(LocalDate weekStart, int entryCount) {
        WeeklyReflectionDTO dto = new WeeklyReflectionDTO();
        dto.setStatus(NOT_ENOUGH_ENTRIES);
        dto.setWeekStart(weekStart);
        dto.setWeekEnd(weekStart.plusDays(6));
        dto.setEntryCount(entryCount);
        return dto;
    }

    public static WeeklyReflectionDTO of(WeeklyReflection r) {
        return new WeeklyReflectionDTO(READY, r.getWeekStart(), r.getWeekStart().plusDays(6), r.getEntryCount(),
                r.getDaysWritten(), r.getThemes(), r.getHabits(), r.getOpening(), r.getQuotes(), r.getQuestion(), r.getCreatedAt());
    }
}
