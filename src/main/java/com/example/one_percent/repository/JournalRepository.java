package com.example.one_percent.repository;

import com.example.one_percent.model.JournalEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JournalRepository extends MongoRepository<JournalEntry, String> {
    List<JournalEntry> findByUserId(String userId);

    /** Looks up an entry only if it belongs to the given user. */
    Optional<JournalEntry> findByIdAndUserId(String id, String userId);
}
