package com.example.one_percent.controller.core;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.service.habit.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/habits")
@CrossOrigin
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @PostMapping("/create")
    public ResponseEntity<HabitDTO> createHabit(@RequestBody HabitDTO habitDTO) {
        HabitDTO createdHabit = habitService.createHabit(habitDTO);
        return ResponseEntity.ok(createdHabit);
    }

    @GetMapping("/me")
    public ResponseEntity<List<HabitDTO>> getHabitsForAuthenticatedUser() {
        List<HabitDTO> habits = habitService.getHabitsByUser();
        System.out.println("[DEBUG] Retrieved habits: " + habits);
        return ResponseEntity.ok(habits);
    }

    @PutMapping("/update")
    public ResponseEntity<HabitDTO> updateHabit(@RequestBody HabitDTO habitDTO) {
        HabitDTO updatedHabit = habitService.updateHabit(habitDTO);
        return ResponseEntity.ok(updatedHabit);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteHabit(@PathVariable String id) {
        habitService.deleteHabit(id);
        return ResponseEntity.noContent().build();
    }

    }
}
