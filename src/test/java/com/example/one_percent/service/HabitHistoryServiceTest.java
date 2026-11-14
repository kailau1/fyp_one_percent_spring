package com.example.one_percent.service;

import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.repository.HabitRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitHistoryServiceTest {

    @Mock
    private HabitHistoryRepository repository;
    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private HabitHistoryService service;

    @BeforeEach
    void setUp() {
        TestAuth.loginAs("alice");
        lenient().when(habitRepository.existsByIdAndUserId("h1", "alice")).thenReturn(true);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
    void refusesToReadOrWriteHistoryForAnotherUsersHabit() {
        when(habitRepository.existsByIdAndUserId("bobs", "alice")).thenReturn(false);

        assertThatThrownBy(() -> service.getHabitHistory("bobs")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markHabitCompleted("bobs")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.unmarkHabitCompleted("bobs")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getTodayHabitStatus("bobs")).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getHabitHistory_onlyReturnsTheCurrentUsersRecords() {
        HabitHistory record = new HabitHistory("rec1", "alice", "h1", LocalDate.now(), true);
        when(repository.findByUserIdAndHabitIdOrderByDateDesc("alice", "h1")).thenReturn(List.of(record));

        assertThat(service.getHabitHistory("h1")).containsExactly(record);
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
