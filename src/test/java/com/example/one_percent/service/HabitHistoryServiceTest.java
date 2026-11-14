package com.example.one_percent.service;

import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.service.habit.HabitHistoryService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitHistoryServiceTest {

    @Mock
    private HabitHistoryRepository repository;

    @InjectMocks
    private HabitHistoryService service;

    @BeforeEach
    void setUp() {
        TestAuth.loginAs("alice");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void markCompleted_createsTodaysRecordWhenNoneExists() {
        when(repository.findByUserIdAndHabitIdAndDate("alice", "h1", LocalDate.now())).thenReturn(Optional.empty());

        HabitHistory saved = service.markHabitCompleted("h1");

        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo("alice");
        assertThat(saved.getHabitId()).isEqualTo("h1");
        assertThat(saved.getDate()).isEqualTo(LocalDate.now());
        assertThat(saved.isCompleted()).isTrue();
    }

    @Test
    void markCompleted_reusesTodaysRecordSoThereAreNoDuplicates() {
        HabitHistory existing = new HabitHistory("rec1", "alice", "h1", LocalDate.now(), false);
        when(repository.findByUserIdAndHabitIdAndDate("alice", "h1", LocalDate.now())).thenReturn(Optional.of(existing));

        HabitHistory saved = service.markHabitCompleted("h1");

        assertThat(saved.getId()).isEqualTo("rec1");
        assertThat(saved.isCompleted()).isTrue();
    }

    @Test
    void unmarkCompleted_setsTodaysRecordToNotCompleted() {
        HabitHistory existing = new HabitHistory("rec1", "alice", "h1", LocalDate.now(), true);
        when(repository.findByUserIdAndHabitIdAndDate("alice", "h1", LocalDate.now())).thenReturn(Optional.of(existing));

        HabitHistory saved = service.unmarkHabitCompleted("h1");

        assertThat(saved.getId()).isEqualTo("rec1");
        assertThat(saved.isCompleted()).isFalse();
    }
}
