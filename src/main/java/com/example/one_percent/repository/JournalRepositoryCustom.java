package com.example.one_percent.repository;

import com.example.one_percent.model.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/** Journal queries that Spring Data can't derive from a method name (see JournalRepositoryImpl). */
public interface JournalRepositoryCustom {

    /**
     * A user's entries, newest first, optionally only those whose title or text contains `text`
     * (case-insensitive, matched literally). Embeddings aren't loaded: lists never need them.
     */
    Page<JournalEntry> searchByUser(String userId, String text, Pageable pageable);

    /**
     * All of a user's entries, newest first, with only what theme matching needs: id, title,
     * createdAt and the embedding (never the text).
     */
    List<JournalEntry> findForThemes(String userId);

    /** When each of a user's entries was written, for entries written in [from, to). */
    List<Instant> findCreatedAtBetween(String userId, Instant from, Instant to);
}
