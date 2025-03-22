package com.example.one_percent.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
}
