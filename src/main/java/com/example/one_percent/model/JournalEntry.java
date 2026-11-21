package com.example.one_percent.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "journal_entries")
public class JournalEntry {

    @Id
    private String id;
    private String userId;
    private String title;
    private String content;
    private List<Double> contentEmbedding;
    private String llmResponse;
    private String llmSummary;
    private Instant createdAt;
    private Boolean wantsFeedback;

    /** The themes this entry was automatically tagged with, and how strongly (best first). */
    private Map<String, Double> themeScores;
    /** Which theme vectors made themeScores; if they've changed since, the tags are worked out again. */
    private String themesVersion;
    /** The user's corrections to the automatic themes. */
    private ThemeOverrides themeOverrides;

    /** Themes the user added to or removed from an entry's automatic ones. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ThemeOverrides {
        private List<String> added;
        private List<String> removed;
    }


}