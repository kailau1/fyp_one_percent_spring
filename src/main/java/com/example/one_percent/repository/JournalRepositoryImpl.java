package com.example.one_percent.repository;

import com.example.one_percent.model.JournalEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Spring Data finds this by name and mixes it into JournalRepository. */
@RequiredArgsConstructor
public class JournalRepositoryImpl implements JournalRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    /**
     * Escapes regex metacharacters so search text is matched literally: searching "c++" or "(draft"
     * must find those characters, not throw or match something else.
     */
    static String literalRegex(String text) {
        return text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    @Override
    public Page<JournalEntry> searchByUser(String userId, String text, Pageable pageable) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (text != null && !text.isBlank()) {
            String pattern = literalRegex(text.trim());
            criteria = criteria.orOperator(
                    Criteria.where("title").regex(pattern, "i"),
                    Criteria.where("content").regex(pattern, "i"));
        }
        long total = mongoTemplate.count(Query.query(criteria), JournalEntry.class);

        Query page = Query.query(criteria)
                // newest first; the id breaks ties so paging never skips or repeats an entry
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .with(pageable);
        page.fields().exclude("contentEmbedding");
        return new PageImpl<>(mongoTemplate.find(page, JournalEntry.class), pageable, total);
    }

    @Override
    public List<Instant> findCreatedAtBetween(String userId, Instant from, Instant to) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("createdAt").gte(from).lt(to));
        query.fields().include("createdAt");
        return mongoTemplate.find(query, JournalEntry.class).stream()
                .map(JournalEntry::getCreatedAt)
                .filter(Objects::nonNull)
                .toList();
    }
}
