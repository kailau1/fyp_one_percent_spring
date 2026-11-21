package com.example.one_percent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/users/me/ai-context: what One Percent's AI knows about the logged-in user, and exactly
 * what it is sent. Lists show everything available, whether or not its switch is on.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiContextDTO {
    /** Habits ticked or changed in the last 7 days, as the AI sees them. */
    private List<String> habits;
    /** The user's most common themes. */
    private List<String> themes;
    /** What the user has told One Percent about themselves, oldest first. */
    private List<Fact> facts;
    private Settings settings;
    /** The context text exactly as it is sent with feedback and prompt requests. */
    private String preview;
    private int maxFacts;
    private int maxFactLength;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fact {
        private String id;
        private String text;
        private Instant createdAt;
    }

    /** Every switch, resolved to true or false. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {
        private boolean useHabits;
        private boolean useThemes;
        private boolean useFacts;
        private boolean usePastAdvice;
    }
}
