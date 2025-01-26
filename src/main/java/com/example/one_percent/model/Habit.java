package com.example.one_percent.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "habits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Habit {
    @Id
    private String id;

    private String userId;
    private String habitName;
    private String description;
    private boolean completed;
}
