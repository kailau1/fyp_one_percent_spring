package com.example.one_percent.config;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.WeeklyReflection;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Creates the indexes the app's queries rely on, at startup (Spring Boot doesn't create indexes
 * from annotations by default). Creating an index that already exists is a no-op.
 */
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // A user's entries, newest first: the journal list, search and pagination read in this order,
        // so MongoDB can walk the index instead of sorting every entry (embeddings and all) in memory.
        mongoTemplate.indexOps(JournalEntry.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .named("userId_createdAt_id"));
        // the reflection history: a user's weeks, newest first
        mongoTemplate.indexOps(WeeklyReflection.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("weekStart", Sort.Direction.DESC)
                .named("userId_weekStart"));
    }
}
