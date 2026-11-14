package com.example.one_percent.repository;

import com.example.one_percent.model.HabitHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitHistoryRepository extends MongoRepository<HabitHistory, String> {
    Optional<HabitHistory> findByUserIdAndHabitIdAndDate(String userId, String habitId, LocalDate date);
    List<HabitHistory> findByUserIdAndDate(String userId, LocalDate date);
    List<HabitHistory> findByUserIdAndHabitIdOrderByDateDesc(String userId, String habitId);

    /**
     * A user's records strictly between two days (both ends EXCLUSIVE, which is how Spring Data
     * MongoDB reads "Between"). A derived query can't use two separate conditions on the same
     * field (DateGreaterThanEqualAndDateLessThanEqual fails at runtime), hence Between.
     */
    List<HabitHistory> findByUserIdAndDateBetweenOrderByDateDesc(String userId, LocalDate after, LocalDate before);
}
