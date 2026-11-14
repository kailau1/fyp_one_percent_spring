package com.example.one_percent.service.habit;

import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.model.User;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HabitHistoryService {

    private final HabitHistoryRepository habitHistoryRepository;
    private final HabitRepository habitRepository;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return user;
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }

    /**
     * Returns the authenticated user, after checking the habit is theirs. Without this, anyone
     * could read or write history for a habit ID belonging to someone else.
     */
    private User requireOwnerOf(String habitId) {
        User user = getAuthenticatedUser();
        if (!habitRepository.existsByIdAndUserId(habitId, user.getId())) {
            throw new ResourceNotFoundException("Habit not found: " + habitId);
        }
        return user;
    }

    public Optional<HabitHistory> getTodayHabitStatus(String habitId) {
        User user = requireOwnerOf(habitId);
        return habitHistoryRepository.findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now());
    }

    public List<HabitHistory> getTodayHabits() {
        User user = getAuthenticatedUser();
        return habitHistoryRepository.findByUserIdAndDate(user.getId(), LocalDate.now());
    }

    public List<HabitHistory> getHabitHistory(String habitId) {
        User user = requireOwnerOf(habitId);
        return habitHistoryRepository.findByUserIdAndHabitIdOrderByDateDesc(user.getId(), habitId);
    }

    /**
     * Every history record for the authenticated user between two days (inclusive), across all
     * their habits. Defaults to the last year. One request instead of one per habit.
     */
    public List<HabitHistory> getHistoryBetween(LocalDate from, LocalDate to) {
        User user = getAuthenticatedUser();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(365);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' must not be after 'to'.");
        }
        return habitHistoryRepository
                .findByUserIdAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(user.getId(), start, end);
    }

    public HabitHistory markHabitCompleted(String habitId) {
        User user = requireOwnerOf(habitId);
        HabitHistory history = habitHistoryRepository
                .findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now())
                .orElse(new HabitHistory(null, user.getId(), habitId, LocalDate.now(), false));

        history.setCompleted(true);
        return habitHistoryRepository.save(history);
    }

    public HabitHistory unmarkHabitCompleted(String habitId) {
        User user = requireOwnerOf(habitId);
        HabitHistory history = habitHistoryRepository
                .findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now())
                .orElse(new HabitHistory(null, user.getId(), habitId, LocalDate.now(), false));

        history.setCompleted(false);
        return habitHistoryRepository.save(history);
    }

}