package com.example.one_percent.repository;

import com.example.one_percent.model.HabitHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitHistoryRepository extends MongoRepository<HabitHistory, String> {
    Optional<HabitHistory> findByUserIdAndHabitIdAndDate(String userId, String habitId, LocalDate date);
    List<HabitHistory> findByUserIdAndDate(String userId, LocalDate date);
    List<HabitHistory> findByHabitIdOrderByDateDesc(String habitId);
}
