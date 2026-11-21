package com.example.one_percent.repository;

import com.example.one_percent.model.User;

/** User updates that touch one field, so they can't overwrite changes made elsewhere (see UserRepositoryImpl). */
public interface UserRepositoryCustom {

    /** Sets only the user's time zone. */
    void updateTimeZone(String userId, String timeZone);

    /** Adds a fact to the user's "about me" list unless it already holds `max`. False if it was full. */
    boolean addFact(String userId, User.AboutMeFact fact, int max);

    /** Removes one of the user's facts. False if they have no fact with that id. */
    boolean removeFact(String userId, String factId);

    /** Replaces the user's AI context switches. */
    void updateAiContext(String userId, User.AiContextSettings settings);
}
