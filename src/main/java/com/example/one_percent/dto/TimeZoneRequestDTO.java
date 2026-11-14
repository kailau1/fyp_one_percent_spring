package com.example.one_percent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of PUT /api/users/me/time-zone. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeZoneRequestDTO {
    /** IANA time zone ID, e.g. "Europe/London" or "America/New_York". */
    private String timeZone;
}
