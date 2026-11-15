package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "habits")
public class Habit {

    @Id
    private String id;
    private String userId;
    private String habitName;
    private String description;
    private String colour;
    private boolean completed;
    private String habitType;
    private String trigger;
    private String action;
    private Instant createdAt;
    private Instant lastUpdated;
}