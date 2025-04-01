package com.example.one_percent.controller;

import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.service.HabitHistoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/habit-history")
@RequiredArgsConstructor
public class HabitHistoryController {

    private final HabitHistoryService habitHistoryService;
    private static final Logger logger = LoggerFactory.getLogger(HabitHistoryController.class);

    @GetMapping("/today/{habitId}")
    public ResponseEntity<Optional<HabitHistory>> getTodayHabitStatus(@PathVariable String habitId) {
        logger.info("Fetching today's habit status for habitId: {}", habitId);
        return ResponseEntity.ok(habitHistoryService.getTodayHabitStatus(habitId));
    }

    @GetMapping("/today")
    public ResponseEntity<List<HabitHistory>> getTodayHabits() {
        logger.info("Fetching today's habits for the authenticated user");
        return ResponseEntity.ok(habitHistoryService.getTodayHabits());
    }

    @GetMapping("/{habitId}")
    public ResponseEntity<List<HabitHistory>> getHabitHistory(@PathVariable String habitId) {
        logger.info("Fetching habit history for habitId: {}", habitId);
        return ResponseEntity.ok(habitHistoryService.getHabitHistory(habitId));
    }

    @PostMapping("/complete/{habitId}")
    public ResponseEntity<HabitHistory> markHabitCompleted(@PathVariable String habitId) {
        logger.info("Marking habit as completed for habitId: {}", habitId);
        return ResponseEntity.ok(habitHistoryService.markHabitCompleted(habitId));
    }

    @PostMapping("/uncomplete/{habitId}")
    public ResponseEntity<HabitHistory> unmarkHabitCompleted(@PathVariable String habitId) {
        logger.info("Unmarking habit as completed for habitId: {}", habitId);
        return ResponseEntity.ok(habitHistoryService.unmarkHabitCompleted(habitId));
    }
    @PostMapping("/add/bulk")
    public ResponseEntity<List<HabitHistory>> addBulkHabitHistory(@RequestBody List<HabitHistory> habitHistories) {
        logger.info("Bulk adding {} habit history records", habitHistories.size());
        List<HabitHistory> savedHistories = habitHistoryService.addBulkHabitHistory(habitHistories);
        return ResponseEntity.ok(savedHistories);
    }
}