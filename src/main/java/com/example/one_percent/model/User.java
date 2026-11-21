package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private List<String> frequentThemes;
    private int themeUsageCount;
    /** IANA time zone, e.g. "Europe/London": where the user's days start and end. Null until set. */
    private String timeZone;
    /** Things the user has told One Percent about themselves, for the AI to keep in mind. */
    private List<AboutMeFact> aboutMe;
    /** Which kinds of context the AI is given about the user; null means all of them. */
    private AiContextSettings aiContext;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AboutMeFact {
        private String id;
        private String text;
        private Instant createdAt;
    }

    /** Each switch is on unless set to false (null = on, so existing users keep today's behaviour). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiContextSettings {
        private Boolean useHabits;
        private Boolean useThemes;
        private Boolean useFacts;
        private Boolean usePastAdvice;

        public static boolean on(Boolean value) {
            return value == null || value;
        }
    }

}
