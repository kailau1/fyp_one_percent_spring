package com.example.one_percent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryDTO {

    private String id;
    private String title;
    private String content;
    private String llmResponse;
    private Instant createdAt;
    /** The themes the entry is about (automatic tags with the user's corrections), strongest first. Read-only. */
    private List<String> themes;
    /** Every theme an entry can have, for a picker. Only on single-entry responses. Read-only. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> themeChoices;
}
