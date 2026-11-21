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

    @Override
    public boolean addFact(String userId, User.AboutMeFact fact, int max) {
        // the size check and the push are one update, so two quick adds can't go over the limit
        Query notFull = Query.query(Criteria.where("_id").is(userId).and("aboutMe." + (max - 1)).exists(false));
        return mongoTemplate.updateFirst(notFull, new Update().push("aboutMe", fact), User.class).getModifiedCount() == 1;
    }

    @Override
    public boolean removeFact(String userId, String factId) {
        Query hasFact = Query.query(Criteria.where("_id").is(userId).and("aboutMe.id").is(factId));
        Update pull = new Update().pull("aboutMe", Query.query(Criteria.where("id").is(factId)).getQueryObject());
        return mongoTemplate.updateFirst(hasFact, pull, User.class).getModifiedCount() == 1;
    }

    @Override
    public void updateAiContext(String userId, User.AiContextSettings settings) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(userId)), Update.update("aiContext", settings), User.class);
    }
}
