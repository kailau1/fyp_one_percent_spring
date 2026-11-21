package com.example.one_percent.service.memory;

import com.example.one_percent.dto.AiContextDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.support.TestAuth;
import com.example.one_percent.util.ContextUtil;
import com.example.one_percent.util.UserClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMemoryServiceTest {

    @Mock
    private ContextUtil contextUtil;
    @Mock
    private UserRepository userRepository;
    private AiMemoryService service;

    @BeforeEach
    void setUp() {
        service = new AiMemoryService(contextUtil, userRepository,
                new UserClock(Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)));
        TestAuth.loginAs("alice");
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void showsEverythingAvailableAndTheExactTextSent() {
        ContextUtil.UserContext ctx = new ContextUtil.UserContext(List.of("Read"), List.of("burnout"),
                List.of(new User.AboutMeFact("f1", "I work nights", Instant.parse("2026-11-01T00:00:00Z"))),
                new User.AiContextSettings(null, false, null, null));
        when(contextUtil.buildUserContext("alice")).thenReturn(ctx);
        when(contextUtil.render(ctx)).thenReturn("the text");

        AiContextDTO dto = service.getContext();

        assertThat(dto.getHabits()).containsExactly("Read");
        assertThat(dto.getThemes()).containsExactly("burnout");
        assertThat(dto.getFacts()).extracting(AiContextDTO.Fact::getText).containsExactly("I work nights");
        assertThat(dto.getSettings()).isEqualTo(new AiContextDTO.Settings(true, false, true, true));
        assertThat(dto.getPreview()).isEqualTo("the text");
        assertThat(dto.getMaxFacts()).isEqualTo(20);
    }

    @Test
    void addsATidiedFactForTheCurrentUser() {
        when(userRepository.addFact(eq("alice"), any(), eq(20))).thenReturn(true);

        AiContextDTO.Fact fact = service.addFact("  Training for a   half marathon \n");

        ArgumentCaptor<User.AboutMeFact> saved = ArgumentCaptor.forClass(User.AboutMeFact.class);
        verify(userRepository).addFact(eq("alice"), saved.capture(), eq(20));
        assertThat(saved.getValue().getText()).isEqualTo("Training for a half marathon");
        assertThat(saved.getValue().getId()).isNotBlank();
        assertThat(fact.getCreatedAt()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
    }

    @Test
    void refusesBlankTooLongOrTooManyFacts() {
        assertThatThrownBy(() -> service.addFact("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addFact(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addFact("x".repeat(201))).hasMessageContaining("200 characters");
        verifyNoInteractions(userRepository);

        when(userRepository.addFact(eq("alice"), any(), eq(20))).thenReturn(false);
        assertThatThrownBy(() -> service.addFact("one more")).hasMessageContaining("up to 20");
    }

    @Test
    void removesOnlyTheCurrentUsersFacts() {
        when(userRepository.removeFact("alice", "f1")).thenReturn(true);
        service.removeFact("f1");
        verify(userRepository).removeFact("alice", "f1");

        when(userRepository.removeFact("alice", "someone-elses")).thenReturn(false);
        assertThatThrownBy(() -> service.removeFact("someone-elses")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void savesTheSwitchesAndReturnsTheUpdatedContext() {
        ContextUtil.UserContext ctx = new ContextUtil.UserContext(List.of(), List.of(), List.of(), new User.AiContextSettings());
        when(contextUtil.buildUserContext("alice")).thenReturn(ctx);

        service.updateSettings(new AiContextDTO.Settings(true, false, true, false));

        verify(userRepository).updateAiContext("alice", new User.AiContextSettings(true, false, true, false));
    }
}
