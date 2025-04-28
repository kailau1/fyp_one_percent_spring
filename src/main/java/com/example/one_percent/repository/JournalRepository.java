package com.example.one_percent.repository;

import com.example.one_percent.model.JournalEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface JournalRepository extends MongoRepository<JournalEntry, String> {
    List<JournalEntry> findByUserId(String userId);

}
