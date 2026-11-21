package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.insights.InsightsController;
import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.insights.InsightsService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightsController.class)
@Import({SecurityConfig.class, WebConfig.class})
class InsightsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InsightsService insightsService;
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

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/insights/themes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(insightsService);
    }

    @Test
    void returnsThemesWithCountsAndExamples() throws Exception {
        when(insightsService.getThemeInsights()).thenReturn(new ThemeInsightsDTO(5, 4, List.of(
                new ThemeInsightsDTO.Theme("burnout", 3, List.of(
                        new ThemeInsightsDTO.EntryRef("j1", "Long week", Instant.parse("2026-11-14T09:00:00Z"))), List.of(1, 2))),
                List.of("2026-10", "2026-11"), List.of(2, 3)));

        mvc.perform(get("/api/insights/themes").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(5))
                .andExpect(jsonPath("$.analysedEntries").value(4))
                .andExpect(jsonPath("$.themes[0].name").value("burnout"))
                .andExpect(jsonPath("$.themes[0].entryCount").value(3))
                .andExpect(jsonPath("$.themes[0].recentEntries[0].id").value("j1"))
                .andExpect(jsonPath("$.themes[0].recentEntries[0].createdAt").value("2026-11-14T09:00:00Z"))
                .andExpect(jsonPath("$.months[1]").value("2026-11"))
                .andExpect(jsonPath("$.entriesPerMonth[1]").value(3))
                .andExpect(jsonPath("$.themes[0].monthlyCounts[0]").value(1));
    }
}
