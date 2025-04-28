package com.example.one_percent.service.habit;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.mapper.HabitMapper;
import com.example.one_percent.model.Habit;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitMapper habitMapper;
    private static final Logger logger = LoggerFactory.getLogger(HabitService.class);


    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return user;
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }

    public HabitDTO createHabit(HabitDTO habitDTO) {
        User user = getAuthenticatedUser();

        Habit habit = habitMapper.toEntity(habitDTO);
        habit.setUserId(user.getId());
        habit.setCreatedAt(LocalDateTime.now());
        habit.setLastUpdated(LocalDateTime.now());

        if ("trigger-action".equalsIgnoreCase(habit.getHabitType())) {
            if (habit.getTrigger() == null || habit.getAction() == null) {
                throw new IllegalArgumentException("Recommended habits must have both a trigger and an action.");
            }
        }

        return habitMapper.toDto(habitRepository.save(habit));
    }

    public List<HabitDTO> getHabitsByUser() {
        User user = getAuthenticatedUser();
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        return habits.stream()
                .map(habitMapper::toDto)
                .collect(Collectors.toList());
    }

    public HabitDTO updateHabit(HabitDTO habitDTO) {
        Optional<Habit> existingHabit = habitRepository.findById(habitDTO.getId());
        if (existingHabit.isPresent()) {
            Habit habitToUpdate = existingHabit.get();
            habitToUpdate.setHabitName(habitDTO.getHabitName());
            habitToUpdate.setDescription(habitDTO.getDescription());
            habitToUpdate.setColour(habitDTO.getColour());
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


}