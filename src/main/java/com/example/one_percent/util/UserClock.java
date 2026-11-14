package com.example.one_percent.util;

import com.example.one_percent.model.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * "Now" and "today" for a given user. A day (habit ticks, "today's entry") starts at midnight in
 * the user's own time zone, not the server's: a server in UTC would otherwise roll someone in
 * New York over to tomorrow at 7pm.
 */
@Component
public class UserClock {

    private final Clock clock;

    public UserClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * True for an IANA region ID such as "Europe/London" (or "UTC"). Fixed offsets like "-04:00"
     * or "GMT+1" are refused: they don't follow daylight saving, so a day would start an hour off
     * for half the year.
     */
    public static boolean isValidZone(String zoneId) {
        return zoneId != null && ZoneId.getAvailableZoneIds().contains(zoneId);
    }

    /**
     * The user's time zone, or the server's own for users who haven't set one yet (which is how
     * every day was counted before time zones were stored).
     */
    public ZoneId zoneOf(User user) {
        String zoneId = user != null ? user.getTimeZone() : null;
        return isValidZone(zoneId) ? ZoneId.of(zoneId) : ZoneId.systemDefault();
    }

    /** The current calendar day where the user is. */
    public LocalDate today(User user) {
        return LocalDate.ofInstant(clock.instant(), zoneOf(user));
    }

    /**
     * The current moment, to the millisecond: MongoDB stores no finer, and some JavaScript engines
     * reject timestamps with more than three fraction digits.
     */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
