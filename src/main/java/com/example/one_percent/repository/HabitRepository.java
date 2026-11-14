package com.example.one_percent.repository;

import com.example.one_percent.model.Habit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends MongoRepository<Habit, String> {
    List<Habit> findByUserId(String userId);

    /** Looks up a habit only if it belongs to the given user. */
    Optional<Habit> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);
}
