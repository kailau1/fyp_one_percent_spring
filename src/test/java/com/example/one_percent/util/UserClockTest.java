package com.example.one_percent.util;

import com.example.one_percent.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserClockTest {

    // 23:30 UTC on 14 Nov: still the 14th in London and New York, already the 15th in Tokyo
    private final UserClock clock = new UserClock(Clock.fixed(Instant.parse("2026-11-14T23:30:00.123456Z"), ZoneOffset.UTC));

    private static User in(String zone) {
        return User.builder().id("u").timeZone(zone).build();
    }

    @Test
    void todayIsTheCalendarDayInTheUsersZone() {
        assertThat(clock.today(in("Europe/London"))).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(clock.today(in("America/New_York"))).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(clock.today(in("Asia/Tokyo"))).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void usersWithoutAValidZoneGetTheServersZone() {
        assertThat(clock.zoneOf(in(null))).isEqualTo(ZoneId.systemDefault());
        assertThat(clock.zoneOf(in("Not/AZone"))).isEqualTo(ZoneId.systemDefault());
        assertThat(clock.zoneOf(null)).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void nowIsCutToMilliseconds() {
        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-14T23:30:00.123Z"));
    }

    @Test
    void recognisesValidZones() {
        assertThat(UserClock.isValidZone("Europe/London")).isTrue();
        assertThat(UserClock.isValidZone("UTC")).isTrue();
        assertThat(UserClock.isValidZone("Mars/Olympus")).isFalse();
        assertThat(UserClock.isValidZone("")).isFalse();
        assertThat(UserClock.isValidZone(null)).isFalse();
    }
}
