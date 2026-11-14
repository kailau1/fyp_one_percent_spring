package com.example.one_percent.repository;

import com.example.one_percent.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** Spring Data finds this by name and mixes it into UserRepository. */
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public void updateTimeZone(String userId, String timeZone) {
        // $set one field: saving the whole (request-time snapshot of the) user could undo other changes
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(userId)), Update.update("timeZone", timeZone), User.class);
    }
}
