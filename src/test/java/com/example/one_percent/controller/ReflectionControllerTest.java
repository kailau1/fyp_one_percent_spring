package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.reflection.ReflectionController;
import com.example.one_percent.dto.PageDTO;
import com.example.one_percent.dto.WeeklyReflectionDTO;
import com.example.one_percent.exception.AiUnavailableException;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.model.WeeklyReflection;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.reflection.ReflectionService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The weekly reflection routes, through the real security chain. */
@WebMvcTest(ReflectionController.class)
@Import({SecurityConfig.class, WebConfig.class})
class ReflectionControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ReflectionService reflectionService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void authenticateAlice() {
        User alice = TestAuth.user("alice");
        when(jwtUtil.extractEmail("valid-token")).thenReturn(alice.getEmail());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(alice);
    }

    private static WeeklyReflectionDTO ready() {
        WeeklyReflection r = new WeeklyReflection("alice:2026-11-09", "alice", LocalDate.of(2026, 11, 9), List.of("e1", "e2"), 2, 2,
                List.of(new WeeklyReflection.ThemeCount("burnout", 2, 1)), List.of(new WeeklyReflection.HabitDays("Run", "#fff", 3)),
                "A week.", List.of(new WeeklyReflection.Quote("e1", "Title", "my words")), "What next?", "m", Instant.parse("2026-11-18T10:00:00Z"));
        return WeeklyReflectionDTO.of(r);
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/reflections/latest")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reflections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reflections/2026-11-09")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reflectionService);
    }

    @Test
    void returnsTheLatestReflection() throws Exception {
        when(reflectionService.latest()).thenReturn(ready());

        mvc.perform(get("/api/reflections/latest").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.weekStart").value("2026-11-09"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-15"))
                .andExpect(jsonPath("$.themes[0].previousFourWeeks").value(1))
                .andExpect(jsonPath("$.habits[0].daysDone").value(3))
                .andExpect(jsonPath("$.quotes[0].entryId").value("e1"))
                .andExpect(jsonPath("$.question").value("What next?"))
                .andExpect(jsonPath("$.entryIds").doesNotExist());
    }

    @Test
    void notEnoughEntriesLeavesTheRestOut() throws Exception {
        when(reflectionService.latest()).thenReturn(WeeklyReflectionDTO.notEnoughEntries(LocalDate.of(2026, 11, 9), 1));

        mvc.perform(get("/api/reflections/latest").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("notEnoughEntries"))
                .andExpect(jsonPath("$.entryCount").value(1))
                .andExpect(jsonPath("$.opening").doesNotExist());
    }

    @Test
    void anAiFailureIsA503WithAMessage() throws Exception {
        when(reflectionService.latest()).thenThrow(new AiUnavailableException("Try again in a minute.", null));

        mvc.perform(get("/api/reflections/latest").header("Authorization", TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Try again in a minute."));
    }

    @Test
    void oneWeekOr404AndABadDateIs400() throws Exception {
        when(reflectionService.forWeek(LocalDate.of(2026, 11, 9))).thenReturn(ready());
        when(reflectionService.forWeek(LocalDate.of(2026, 11, 2))).thenThrow(new ResourceNotFoundException("none"));

        mvc.perform(get("/api/reflections/2026-11-09").header("Authorization", TOKEN)).andExpect(status().isOk());
        mvc.perform(get("/api/reflections/2026-11-02").header("Authorization", TOKEN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reflections/last-week").header("Authorization", TOKEN)).andExpect(status().isBadRequest());
    }

    @Test
    void history() throws Exception {
        when(reflectionService.history(0, 10)).thenReturn(new PageDTO<>(List.of(ready()), 0, 10, 1, 1, false));

        mvc.perform(get("/api/reflections").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].weekStart").value("2026-11-09"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }
}
