package com.example.one_percent.dto;

import lombok.*;
import java.time.Instant;

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
}
