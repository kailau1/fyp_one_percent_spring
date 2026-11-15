package com.example.one_percent.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    /** IANA time zone, e.g. "Europe/London", or null if the user hasn't set one. */
    private String timeZone;
}
