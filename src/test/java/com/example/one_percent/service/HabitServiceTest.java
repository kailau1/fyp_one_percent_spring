package com.example.one_percent.service;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.mapper.HabitMapper;
import com.example.one_percent.model.Habit;
import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.service.habit.HabitService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitHistoryRepository habitHistoryRepository;

    private final HabitMapper mapper = Mappers.getMapper(HabitMapper.class);
    private HabitService habitService;

    @BeforeEach
    void setUp() {
        habitService = new HabitService(habitRepository, mapper, habitHistoryRepository);
        TestAuth.loginAs("alice");
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private HabitDTO dto(String type, String name, String trigger, String action) {
        HabitDTO d = new HabitDTO();
        d.setHabitType(type);
        d.setHabitName(name);
        d.setTrigger(trigger);
        d.setAction(action);
        d.setColour("#A7C7E7");
        return d;
    }

    @Test
    void createHabit_assignsOwnerAndTimestamps() {
        when(habitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        HabitDTO request = dto("standard", "Read", null, null);
        request.setUserId("someone-else"); // must be overridden by the token's user

        HabitDTO created = habitService.createHabit(request);

        assertThat(created.getUserId()).isEqualTo("alice");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getLastUpdated()).isNotNull();
        assertThat(created.getColour()).isEqualTo("#A7C7E7");
    }

    @Test
    void createHabit_triggerActionHabitNeedsBothParts() {
        assertThatThrownBy(() -> habitService.createHabit(dto("trigger-action", null, "pour coffee", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> habitService.createHabit(dto("trigger-action", null, null, "meditate")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(habitRepository, never()).save(any());
    }

    @Test
    void createHabit_acceptsCompleteTriggerActionHabit() {
        when(habitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HabitDTO created = habitService.createHabit(dto("trigger-action", null, "pour coffee", "meditate"));

        assertThat(created.getTrigger()).isEqualTo("pour coffee");
        assertThat(created.getAction()).isEqualTo("meditate");
    }

    @Test
    void getHabitsByUser_onlyReturnsCurrentUsersHabits() {
        Habit h = new Habit("h1", "alice", "Read", null, "#fff", false, "standard", null, null, null, null);
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(h));

        assertThat(habitService.getHabitsByUser()).extracting(HabitDTO::getId).containsExactly("h1");
    }

    @Test
    void getHabitsByUser_marksHabitsCompletedFromTodaysHistory() {
        // the stored flag is deliberately wrong on both: only today's history counts
        Habit ticked = new Habit("h1", "alice", "Read", null, "#fff", false, "standard", null, null, null, null);
        Habit unticked = new Habit("h2", "alice", "Run", null, "#fff", true, "standard", null, null, null, null);
        Habit untickedAgain = new Habit("h3", "alice", "Stretch", null, "#fff", false, "standard", null, null, null, null);
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(ticked, unticked, untickedAgain));
        when(habitHistoryRepository.findByUserIdAndDate("alice", LocalDate.now())).thenReturn(List.of(
                new HabitHistory("r1", "alice", "h1", LocalDate.now(), true),
                new HabitHistory("r3", "alice", "h3", LocalDate.now(), false)));  // ticked then un-ticked

        List<HabitDTO> result = habitService.getHabitsByUser();

        assertThat(result).extracting(HabitDTO::getId, HabitDTO::isCompleted)
                .containsExactly(tuple("h1", true), tuple("h2", false), tuple("h3", false));
    }

    @Test
    void updateHabit_keepsTodaysCompletionWhateverTheClientSends() {
        Habit existing = new Habit("h1", "alice", "Read", null, "#fff", false, "standard", null, null, null, null);
        when(habitRepository.findByIdAndUserId("h1", "alice")).thenReturn(Optional.of(existing));
        when(habitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(habitHistoryRepository.findByUserIdAndHabitIdAndDate("alice", "h1", LocalDate.now()))
                .thenReturn(Optional.of(new HabitHistory("r1", "alice", "h1", LocalDate.now(), true)));
        HabitDTO update = dto("standard", "Read more", null, null);
        update.setId("h1");
        update.setCompleted(false); // e.g. an older app build that doesn't send it

        assertThat(habitService.updateHabit(update).isCompleted()).isTrue();
    }

    @Test
    void updateHabit_updatesFieldsAndBumpsLastUpdated() {
        Habit existing = new Habit("h1", "alice", "Read", null, "#fff", false, "standard", null, null, null, null);
        when(habitRepository.findByIdAndUserId("h1", "alice")).thenReturn(Optional.of(existing));
        when(habitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        HabitDTO update = dto("standard", "Read more", null, null);
        update.setId("h1");

        habitService.updateHabit(update);

        ArgumentCaptor<Habit> saved = ArgumentCaptor.forClass(Habit.class);
        verify(habitRepository).save(saved.capture());
        assertThat(saved.getValue().getHabitName()).isEqualTo("Read more");
        assertThat(saved.getValue().getLastUpdated()).isNotNull();
    }

    @Test
    void updateHabit_refusesAnotherUsersHabit() {
        // bob's habit: the owner-scoped lookup finds nothing for alice
        when(habitRepository.findByIdAndUserId("bobs", "alice")).thenReturn(Optional.empty());
        HabitDTO update = dto("standard", "hacked", null, null);
        update.setId("bobs");

        assertThatThrownBy(() -> habitService.updateHabit(update)).isInstanceOf(ResourceNotFoundException.class);
        verify(habitRepository, never()).save(any());
        verify(habitRepository, never()).findById(any());
    }

    @Test
    void deleteHabit_deletesOwnHabit() {
        Habit mine = new Habit("h1", "alice", "Read", null, "#fff", false, "standard", null, null, null, null);
        when(habitRepository.findByIdAndUserId("h1", "alice")).thenReturn(Optional.of(mine));

        habitService.deleteHabit("h1");

        verify(habitRepository).delete(mine);
    }

    @Test
    void deleteHabit_refusesAnotherUsersOrMissingHabit() {
        when(habitRepository.findByIdAndUserId("bobs", "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.deleteHabit("bobs")).isInstanceOf(ResourceNotFoundException.class);
        verify(habitRepository, never()).delete(any());
        verify(habitRepository, never()).deleteById(any());
    }

    @Test
    void createHabit_ignoresAClientSuppliedId() {
        when(habitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        HabitDTO request = dto("standard", "Read", null, null);
        request.setId("someone-elses-habit-id");

        assertThat(habitService.createHabit(request).getId()).isNull();
    }
}
