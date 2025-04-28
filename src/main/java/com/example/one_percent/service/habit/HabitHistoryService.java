package com.example.one_percent.service.habit;

import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.HabitHistoryRepository;
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

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return user;
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }

    public Optional<HabitHistory> getTodayHabitStatus(String habitId) {
        User user = getAuthenticatedUser();
        return habitHistoryRepository.findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now());
    }

    public List<HabitHistory> getTodayHabits() {
        User user = getAuthenticatedUser();
        return habitHistoryRepository.findByUserIdAndDate(user.getId(), LocalDate.now());
    }

    public List<HabitHistory> getHabitHistory(String habitId) {
        return habitHistoryRepository.findByHabitIdOrderByDateDesc(habitId);
    }

    public HabitHistory markHabitCompleted(String habitId) {
        User user = getAuthenticatedUser();
        HabitHistory history = habitHistoryRepository
                .findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now())
                .orElse(new HabitHistory(null, user.getId(), habitId, LocalDate.now(), false));

        history.setCompleted(true);
        return habitHistoryRepository.save(history);
    }

    public HabitHistory unmarkHabitCompleted(String habitId) {
        User user = getAuthenticatedUser();
        HabitHistory history = habitHistoryRepository
                .findByUserIdAndHabitIdAndDate(user.getId(), habitId, LocalDate.now())
                .orElse(new HabitHistory(null, user.getId(), habitId, LocalDate.now(), false));

        history.setCompleted(false);
        return habitHistoryRepository.save(history);
    }

    public List<HabitHistory> addBulkHabitHistory(List<HabitHistory> histories) {
        return habitHistoryRepository.saveAll(histories);
    }

}