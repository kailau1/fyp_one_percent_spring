package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "habit_history")
public class HabitHistory {

    @Id
    private String id;

    private String userId;
    private String habitId;
    private LocalDate date;
    private boolean completed;
}
