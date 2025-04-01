package com.example.one_percent.service;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HabitResetService {

    private final HabitRepository habitRepository;
    private static final Logger logger = LoggerFactory.getLogger(HabitResetService.class);

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetHabitCompletionStatus() {
        logger.info("Resetting habit completion status for a new day");

        List<Habit> habits = habitRepository.findAll();
        for (Habit habit : habits) {
            habit.setCompleted(false);
            habitRepository.save(habit);
        }

        logger.info("Habit completion status reset for {} habits", habits.size());
    }
}