package com.example.one_percent.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

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
    private LocalDateTime createdAt;
    private Boolean wantsFeedback;


}