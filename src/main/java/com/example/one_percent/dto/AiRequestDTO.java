package com.example.one_percent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestDTO {
    private String journalId;
    private String journalContent;
    // No userId: the user always comes from the JWT, never from the request body.
    // (Older app builds still send one; Jackson ignores unknown fields.)
}
