package com.example.one_percent.service;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HabitService {
    private final HabitRepository habitRepository;

    public Habit createHabit(Habit habit) {
        return habitRepository.save(habit);
    }

    public List<Habit> getHabitsByUser(String userId) {
        return habitRepository.findByUserId(userId);
    }

    public Habit updateHabit(Habit habit) {
        return habitRepository.save(habit);
    }

    public void deleteHabit(String id) {
        habitRepository.deleteById(id);
    }
}
