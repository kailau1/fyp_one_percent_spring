package com.example.one_percent.repository;

import com.example.one_percent.model.WeeklyReflection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WeeklyReflectionRepository extends MongoRepository<WeeklyReflection, String> {

    /** A user's reflections, newest week first. */
    Page<WeeklyReflection> findByUserIdOrderByWeekStartDesc(String userId, Pageable pageable);
}
