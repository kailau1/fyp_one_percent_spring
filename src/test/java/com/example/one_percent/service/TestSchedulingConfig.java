package com.example.one_percent.service;

import com.example.one_percent.model.Habit;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.service.habit.HabitResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = TestSchedulingConfig.class)
public class TestSchedulingConfig {

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private HabitResetService habitResetService;

    private List<Habit> habits;

    @BeforeEach
    public void setUp() {
        Habit habit1 = new Habit("1", "user1", "Habit 1", "Description 1", "red", true, "type1", "trigger1", "action1", null, null);
        Habit habit2 = new Habit("2", "user2", "Habit 2", "Description 2", "blue", true, "type2", "trigger2", "action2", null, null);
        habits = Arrays.asList(habit1, habit2);

        when(habitRepository.findAll()).thenReturn(habits);
    }

    @Test
    public void testResetHabitCompletionStatus() {
        habitResetService.resetHabitCompletionStatus();

        for (Habit habit : habits) {
            habit.setCompleted(false);
        }

        verify(habitRepository, times(1)).findAll();
        verify(habitRepository, times(2)).save(any(Habit.class));
    }
}