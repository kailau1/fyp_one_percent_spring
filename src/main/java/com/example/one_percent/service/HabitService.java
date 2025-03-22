package com.example.one_percent.service;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.mapper.HabitMapper;
import com.example.one_percent.model.Habit;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitMapper habitMapper;
    private final UserRepository userRepository;

    @PostConstruct
    public void debugMapper() {
        System.out.println("[DEBUG] Injected HabitMapper = " + habitMapper);
    }

    public HabitDTO createHabit(HabitDTO habitDTO) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email);

        Habit habit = habitMapper.toEntity(habitDTO);
        habit.setUserId(user.getId());
        habit.setCreatedAt(LocalDateTime.now());
        habit.setLastUpdated(LocalDateTime.now());

        if ("RECOMMENDED".equalsIgnoreCase(habit.getHabitType())) {
            if (habit.getTrigger() == null || habit.getAction() == null) {
                throw new IllegalArgumentException("Recommended habits must have both a trigger and an action.");
            }
        }

        return habitMapper.toDto(habitRepository.save(habit));
    }

    public List<HabitDTO> getHabitsByUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {
            String userId = user.getId();

            List<Habit> habits = habitRepository.findByUserId(userId);

            return habits.stream()
                    .map(habitMapper::toDto)
                    .collect(Collectors.toList());
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }


    public HabitDTO updateHabit(HabitDTO habitDTO) {
        Optional<Habit> existingHabit = habitRepository.findById(habitDTO.getId());
        if (existingHabit.isPresent()) {
            Habit habitToUpdate = existingHabit.get();
            habitToUpdate.setHabitName(habitDTO.getHabitName());
            habitToUpdate.setDescription(habitDTO.getDescription());
            habitToUpdate.setCompleted(habitDTO.isCompleted());
            habitToUpdate.setHabitType(habitDTO.getHabitType());
            habitToUpdate.setTrigger(habitDTO.getTrigger());
            habitToUpdate.setAction(habitDTO.getAction());
            habitToUpdate.setLastUpdated(LocalDateTime.now());

            return habitMapper.toDto(habitRepository.save(habitToUpdate));
        }
        throw new IllegalArgumentException("Habit not found with ID: " + habitDTO.getId());
    }

    public void deleteHabit(String id) {
        if (habitRepository.existsById(id)) {
            habitRepository.deleteById(id);
        } else {
            throw new IllegalArgumentException("Habit not found with ID: " + id);
        }
    }

    public HabitDTO completeHabit(String id) {
        Optional<Habit> habitOptional = habitRepository.findById(id);
        if (habitOptional.isPresent()) {
            Habit habit = habitOptional.get();
            habit.setCompleted(true);
            habit.setLastUpdated(LocalDateTime.now());
            return habitMapper.toDto(habitRepository.save(habit));
        }
        throw new IllegalArgumentException("Habit not found with ID: " + id);
    }

    public HabitDTO uncompleteHabit(String id) {
        Optional<Habit> habitOptional = habitRepository.findById(id);
        if (habitOptional.isPresent()) {
            Habit habit = habitOptional.get();
            habit.setCompleted(false);
            habit.setLastUpdated(LocalDateTime.now());
            return habitMapper.toDto(habitRepository.save(habit));
        }
        throw new IllegalArgumentException("Habit not found with ID: " + id);
    }
}
