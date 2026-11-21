package com.example.one_percent.repository;

import com.example.one_percent.model.User;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The fact updates are single atomic MongoDB updates scoped to the user; check what they send. */
class UserRepositoryImplTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(mongo);

    private ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    private ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);

    @Test
    void addingAFactChecksTheLimitInTheSameUpdate() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(User.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));

        boolean added = repository.addFact("alice", new User.AboutMeFact("f1", "text", Instant.EPOCH), 20);

        assertThat(added).isTrue();
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(User.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"_id\": \"alice\"").contains("\"aboutMe.19\": {\"$exists\": false}");
        assertThat(update.getValue().getUpdateObject()).containsKey("$push"); // the fact object itself is encoded by Spring when sent
    }

    @Test
    void aFullListAddsNothing() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(User.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(repository.addFact("alice", new User.AboutMeFact("f1", "text", Instant.EPOCH), 20)).isFalse();
    }

    @Test
    void removingAFactOnlyMatchesTheUsersOwn() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(User.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(repository.removeFact("alice", "f9")).isFalse();
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(User.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"_id\": \"alice\"").contains("\"aboutMe.id\": \"f9\"");
        assertThat(update.getValue().getUpdateObject().toJson()).contains("\"$pull\": {\"aboutMe\": {\"id\": \"f9\"}}");
    }
}
