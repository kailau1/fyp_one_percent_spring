package com.example.one_percent.service.habit;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.mapper.HabitMapper;
import com.example.one_percent.model.Habit;
import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitMapper habitMapper;
    private final HabitHistoryRepository habitHistoryRepository;
    private final UserClock userClock;
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

    /** Fetches a habit only if it belongs to the authenticated user; otherwise 404 (see JournalService). */
    private Habit getOwnedHabit(String id) {
        User user = getAuthenticatedUser();
        return habitRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found: " + id));
    }

    public HabitDTO createHabit(HabitDTO habitDTO) {
        User user = getAuthenticatedUser();

        Habit habit = habitMapper.toEntity(habitDTO);
        habit.setId(null); // never let the client choose (or overwrite) an ID on create
        habit.setUserId(user.getId());
        habit.setCreatedAt(userClock.now());
        habit.setLastUpdated(userClock.now());

        if ("trigger-action".equalsIgnoreCase(habit.getHabitType())) {
            if (habit.getTrigger() == null || habit.getAction() == null) {
                throw new IllegalArgumentException("Recommended habits must have both a trigger and an action.");
            }
        }

        habit.setCompleted(false);

        return habitMapper.toDto(habitRepository.save(habit));
    }

    /**
     * "Completed" always means "completed today", and today's habit_history record is the single
     * source of truth for it. The complete/uncomplete endpoints only ever wrote history, so the
     * flag stored on the habit was never set and every tick vanished the next time habits were loaded.
     */
    public List<HabitDTO> getHabitsByUser() {
        User user = getAuthenticatedUser();
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        Set<String> completedToday = habitHistoryRepository.findByUserIdAndDate(user.getId(), userClock.today(user))
                .stream()
                .filter(HabitHistory::isCompleted)
                .map(HabitHistory::getHabitId)
                .collect(Collectors.toSet());

        return habits.stream()
                .map(habit -> toDto(habit, completedToday.contains(habit.getId())))
                .collect(Collectors.toList());
    }

    private HabitDTO toDto(Habit habit, boolean completedToday) {
        HabitDTO dto = habitMapper.toDto(habit);
        dto.setCompleted(completedToday);
        return dto;
    }

    public HabitDTO updateHabit(HabitDTO habitDTO) {
        Habit habitToUpdate = getOwnedHabit(habitDTO.getId());
        habitToUpdate.setHabitName(habitDTO.getHabitName());
        habitToUpdate.setDescription(habitDTO.getDescription());
        habitToUpdate.setColour(habitDTO.getColour());
        habitToUpdate.setHabitType(habitDTO.getHabitType());
        habitToUpdate.setTrigger(habitDTO.getTrigger());
        habitToUpdate.setAction(habitDTO.getAction());
        habitToUpdate.setLastUpdated(userClock.now());

        // Editing a habit never changes whether it's done today (that used to un-tick it).
        boolean completedToday = habitHistoryRepository
                .findByUserIdAndHabitIdAndDate(habitToUpdate.getUserId(), habitToUpdate.getId(), userClock.today(getAuthenticatedUser()))
                .map(HabitHistory::isCompleted)
                .orElse(false);

        return toDto(habitRepository.save(habitToUpdate), completedToday);
    }

    public void deleteHabit(String id) {
        habitRepository.delete(getOwnedHabit(id));
    }


}