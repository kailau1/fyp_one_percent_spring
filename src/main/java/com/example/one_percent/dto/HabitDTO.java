package com.example.one_percent.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitDTO {
    private String id;
    private String userId;
    private String habitName;
    private String description;
    private boolean completed;
    private String habitType;
    private String trigger;
    private String action;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}
