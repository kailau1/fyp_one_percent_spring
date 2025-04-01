package com.example.one_percent.dto;

import lombok.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitDTO {

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
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}
