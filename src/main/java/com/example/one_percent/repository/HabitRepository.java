package com.example.one_percent.repository;

import com.example.one_percent.model.Habit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HabitRepository extends MongoRepository<Habit, String> {
    List<Habit> findByUserId(String userId);
}
