package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.core.HabitHistoryController;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.habit.HabitHistoryService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HabitHistoryController.class)
@Import({SecurityConfig.class, WebConfig.class})
class HabitHistoryControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HabitHistoryService habitHistoryService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void authenticate() {
        User alice = TestAuth.user("alice");
        when(jwtUtil.extractEmail("valid-token")).thenReturn(alice.getEmail());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(alice);
    }

    @Test
    void returnsHistoryWithDatesAsPlainCalendarDays() throws Exception {
        when(habitHistoryService.getHabitHistory("h1"))
                .thenReturn(List.of(new HabitHistory("r1", "alice", "h1", LocalDate.of(2025, 4, 20), true)));

        mvc.perform(get("/api/habit-history/h1").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2025-04-20"))
                .andExpect(jsonPath("$[0].completed").value(true));
    }

    @Test
    void anotherUsersHabitIs404() throws Exception {
        when(habitHistoryService.getHabitHistory("bobs")).thenThrow(new ResourceNotFoundException("Habit not found: bobs"));
        when(habitHistoryService.markHabitCompleted("bobs")).thenThrow(new ResourceNotFoundException("Habit not found: bobs"));

        mvc.perform(get("/api/habit-history/bobs").header("Authorization", TOKEN)).andExpect(status().isNotFound());
        mvc.perform(post("/api/habit-history/complete/bobs").header("Authorization", TOKEN)).andExpect(status().isNotFound());
    }

    @Test
    void bulkInsertEndpointNoLongerExists() throws Exception {
        mvc.perform(post("/api/habit-history/add/bulk").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"userId\":\"bob\",\"habitId\":\"x\",\"date\":\"2025-04-20\",\"completed\":true}]"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(habitHistoryService);
    }
}
