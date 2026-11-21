package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The tip of the day for one calendar day, shared by everyone. Keyed by the day ("2026-11-21"),
 * so there can only ever be one per day, even if two requests race to create it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "daily_tips")
public class DailyTip {

    /** The day, as "YYYY-MM-DD". */
    @Id
    private String day;
    private String tip;
    private Instant createdAt;
}
