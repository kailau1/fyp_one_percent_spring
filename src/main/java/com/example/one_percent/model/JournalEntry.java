package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@Document(collection = "journal_entries")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    private String id;
    private String userId;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    private String llmResponse;

}
