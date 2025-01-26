package com.example.one_percent.controller;

import com.example.one_percent.model.Habit;
import com.example.one_percent.service.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/habits")
@CrossOrigin
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping("/create")
    public ResponseEntity<Habit> createHabit(@RequestBody Habit habit) {
        return ResponseEntity.ok(habitService.createHabit(habit));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getHabitsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(habitService.getHabitsByUser(userId));
    }

    @PutMapping("/update")
    public ResponseEntity<Habit> updateHabit(@RequestBody Habit habit) {
        return ResponseEntity.ok(habitService.updateHabit(habit));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteHabit(@PathVariable String id) {
        habitService.deleteHabit(id);
        return ResponseEntity.ok().build();
    }
}
