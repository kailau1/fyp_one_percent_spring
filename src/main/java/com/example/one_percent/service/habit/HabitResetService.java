package com.example.one_percent.service.habit;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HabitResetService {

    private final HabitRepository habitRepository;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetHabitCompletionStatus() {

        List<Habit> habits = habitRepository.findAll();
        for (Habit habit : habits) {
            habit.setCompleted(false);
            habitRepository.save(habit);
        }

    }
}