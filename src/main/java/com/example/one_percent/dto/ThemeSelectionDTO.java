package com.example.one_percent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The full set of themes the user wants an entry to have (PUT /api/journals/entry/{id}/themes). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeSelectionDTO {
    private List<String> themes;
}
