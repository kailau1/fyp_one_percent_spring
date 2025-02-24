package com.example.one_percent.service;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;

    public Habit createHabit(Habit habit) {
        habit.setCreatedAt(LocalDateTime.now());
        habit.setLastUpdated(LocalDateTime.now());

        if ("RECOMMENDED".equalsIgnoreCase(habit.getHabitType())) {
            if (habit.getTrigger() == null || habit.getAction() == null) {
                throw new IllegalArgumentException("Recommended habits must have both a trigger and an action.");
            }
        }

        return habitRepository.save(habit);
    }


    public List<Habit> getHabitsByUser(String userId) {
        return habitRepository.findByUserId(userId);
    }

    public Habit updateHabit(Habit habit) {
        Optional<Habit> existingHabit = habitRepository.findById(habit.getId());
        if (existingHabit.isPresent()) {
            Habit habitToUpdate = existingHabit.get();
            habitToUpdate.setHabitName(habit.getHabitName());
            habitToUpdate.setDescription(habit.getDescription());
            habitToUpdate.setCompleted(habit.isCompleted());
            habitToUpdate.setHabitType(habit.getHabitType());
            habitToUpdate.setTrigger(habit.getTrigger());
            habitToUpdate.setAction(habit.getAction());
            habitToUpdate.setLastUpdated(LocalDateTime.now());

            return habitRepository.save(habitToUpdate);
        }
        throw new IllegalArgumentException("Habit not found with ID: " + habit.getId());
    }


    public void deleteHabit(String id) {
        if (habitRepository.existsById(id)) {
            habitRepository.deleteById(id);
        } else {
            throw new IllegalArgumentException("Habit not found with ID: " + id);
        }
    }

    public Habit completeHabit(String id) {
        Optional<Habit> habitOptional = habitRepository.findById(id);
        if (habitOptional.isPresent()) {
            Habit habit = habitOptional.get();
            habit.setCompleted(true);
            habit.setLastUpdated(LocalDateTime.now());
            return habitRepository.save(habit);
        }
        throw new IllegalArgumentException("Habit not found with ID: " + id);
    }

    public Habit uncompleteHabit(String id) {
        Optional<Habit> habitOptional = habitRepository.findById(id);
        if (habitOptional.isPresent()) {
            Habit habit = habitOptional.get();
            habit.setCompleted(false);
            habit.setLastUpdated(LocalDateTime.now());
            return habitRepository.save(habit);
        }
        throw new IllegalArgumentException("Habit not found with ID: " + id);
    }
}
