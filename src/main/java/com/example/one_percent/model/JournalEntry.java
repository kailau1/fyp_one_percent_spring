package com.example.one_percent.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@Document(collection = "journal_entries")
public class JournalEntry {

    @Id
    private String id;
    private String userId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}
