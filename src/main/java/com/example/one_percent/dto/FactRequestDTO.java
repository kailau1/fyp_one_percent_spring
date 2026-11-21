package com.example.one_percent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST /api/users/me/facts */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactRequestDTO {
    private String text;
}
