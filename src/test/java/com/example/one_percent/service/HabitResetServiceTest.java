package com.example.one_percent.service;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.service.habit.HabitResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitResetServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private HabitResetService habitResetService;

    @Test
    void resetsEveryHabitToNotCompleted() {
        Habit done = new Habit("1", "user1", "Read", "desc", "red", true, "standard", null, null, null, null);
        Habit notDone = new Habit("2", "user2", "Run", "desc", "blue", false, "standard", null, null, null, null);
        when(habitRepository.findAll()).thenReturn(List.of(done, notDone));

        habitResetService.resetHabitCompletionStatus();

        assertThat(done.isCompleted()).isFalse();
        assertThat(notDone.isCompleted()).isFalse();
        verify(habitRepository, times(2)).save(any(Habit.class));
    }

    @Test
    void doesNothingWhenThereAreNoHabits() {
        when(habitRepository.findAll()).thenReturn(List.of());

        habitResetService.resetHabitCompletionStatus();

        verify(habitRepository, never()).save(any());
    }
}
