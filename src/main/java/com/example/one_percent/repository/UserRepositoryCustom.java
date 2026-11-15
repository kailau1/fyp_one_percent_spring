package com.example.one_percent.repository;

/** User updates that touch one field, so they can't overwrite changes made elsewhere (see UserRepositoryImpl). */
public interface UserRepositoryCustom {

    /** Sets only the user's time zone. */
    void updateTimeZone(String userId, String timeZone);
}
