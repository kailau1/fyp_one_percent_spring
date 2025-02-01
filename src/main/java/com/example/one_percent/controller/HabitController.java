package com.example.one_percent.controller;

import com.example.one_percent.model.Habit;
import com.example.one_percent.service.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
@CrossOrigin
public class HabitController {

    private final HabitService habitService;
    
    @PostMapping("/create")
    public ResponseEntity<Habit> createHabit(@RequestBody Habit habit) {
        Habit createdHabit = habitService.createHabit(habit);
        return ResponseEntity.ok(createdHabit);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Habit>> getHabitsByUser(@PathVariable String userId) {
        List<Habit> habits = habitService.getHabitsByUser(userId);
        return ResponseEntity.ok(habits);
    }

    @PutMapping("/update")
    public ResponseEntity<Habit> updateHabit(@RequestBody Habit habit) {
        Habit updatedHabit = habitService.updateHabit(habit);
        return ResponseEntity.ok(updatedHabit);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteHabit(@PathVariable String id) {
        habitService.deleteHabit(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/completed/{id}")
    public ResponseEntity<Habit> completeHabit(@PathVariable String id) {
        Habit completedHabit = habitService.completeHabit(id);
        System.out.println(completedHabit);
        return ResponseEntity.ok(completedHabit);
    }

    @PostMapping("/uncomplete/{id}")
    public ResponseEntity<Habit> uncompleteHabit(@PathVariable String id) {
        Habit uncompletedHabit = habitService.uncompleteHabit(id);
        System.out.println(uncompletedHabit);
        return ResponseEntity.ok(uncompletedHabit);
    }
}
