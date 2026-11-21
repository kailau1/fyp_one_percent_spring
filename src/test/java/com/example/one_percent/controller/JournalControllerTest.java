package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.core.JournalController;
import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.dto.PageDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain + JWT filter around JournalController,
 * with the service mocked. Checks auth is required and ownership failures become 404s.
 */
@WebMvcTest(JournalController.class)
@Import({SecurityConfig.class, WebConfig.class})
class JournalControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JournalService journalService;
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
    void rejectsRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/journals/entry/j1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(journalService);
    }

    @Test
    void searchReturnsAPageWithDefaults() throws Exception {
        when(journalService.searchJournals(null, 0, 20)).thenReturn(new PageDTO<>(
                java.util.List.of(JournalEntryDTO.builder().id("j1").title("Mine").build()), 0, 20, 1, 1, false));

        mvc.perform(get("/api/journals").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("j1"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void searchPassesTheQueryAndPaging() throws Exception {
        when(journalService.searchJournals("walk", 2, 5)).thenReturn(new PageDTO<>(java.util.List.of(), 2, 5, 0, 0, false));

        mvc.perform(get("/api/journals").param("q", "walk").param("page", "2").param("size", "5").header("Authorization", TOKEN))
                .andExpect(status().isOk());
        verify(journalService).searchJournals("walk", 2, 5);
    }

    @Test
    void badPagingIs400() throws Exception {
        when(journalService.searchJournals(null, 0, 500)).thenThrow(new IllegalArgumentException("'size' must be between 1 and 100."));

        mvc.perform(get("/api/journals").param("size", "500").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/journals").param("page", "abc").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIs400NotA500() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/journals")
                        .header("Authorization", TOKEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(journalService);
    }

    @Test
    void searchNeedsAToken() throws Exception {
        mvc.perform(get("/api/journals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/journals/days")).andExpect(status().isUnauthorized());
        verifyNoInteractions(journalService);
    }

    @Test
    void daysAreCalendarDates() throws Exception {
        when(journalService.getDaysWithEntries(java.time.LocalDate.of(2026, 11, 1), null))
                .thenReturn(java.util.List.of(java.time.LocalDate.of(2026, 11, 3)));

        mvc.perform(get("/api/journals/days").param("from", "2026-11-01").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2026-11-03"));
        mvc.perform(get("/api/journals/days").param("from", "yesterday").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsOwnedEntry() throws Exception {
        when(journalService.getJournalById("j1"))
                .thenReturn(JournalEntryDTO.builder().id("j1").title("Mine").content("c").build());

        mvc.perform(get("/api/journals/entry/j1").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("j1"))
                .andExpect(jsonPath("$.title").value("Mine"));
    }

    @Test
    void returns404WhenFetchingAnotherUsersEntry() throws Exception {
        when(journalService.getJournalById("bobs")).thenThrow(new ResourceNotFoundException("Journal entry not found: bobs"));

        mvc.perform(get("/api/journals/entry/bobs").header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendsTimestampsInUtcWithTheOffset() throws Exception {
        // a moment in time, so every client shows it in its own zone (no zone-less local time)
        when(journalService.getJournalById("j1")).thenReturn(JournalEntryDTO.builder()
                .id("j1").content("c").createdAt(java.time.Instant.parse("2026-11-14T09:30:00.123Z")).build());

        mvc.perform(get("/api/journals/entry/j1").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value("2026-11-14T09:30:00.123Z"));
    }

    @Test
    void setsAnEntrysThemes() throws Exception {
        when(journalService.setThemes("j1", List.of("anxiety", "gratitude")))
                .thenReturn(JournalEntryDTO.builder().id("j1").themes(List.of("anxiety", "gratitude")).build());

        mvc.perform(put("/api/journals/entry/j1/themes").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themes\":[\"anxiety\",\"gratitude\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themes[1]").value("gratitude"));
    }

    @Test
    void settingAnUnknownThemeIs400AndAnotherUsersEntryIs404() throws Exception {
        when(journalService.setThemes("j1", List.of("made-up"))).thenThrow(new IllegalArgumentException("Unknown theme: made-up."));
        when(journalService.setThemes("bobs", List.of())).thenThrow(new ResourceNotFoundException("Journal entry not found: bobs"));

        mvc.perform(put("/api/journals/entry/j1/themes").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"themes\":[\"made-up\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/journals/entry/bobs/themes").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"themes\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void settingThemesRequiresLogin() throws Exception {
        mvc.perform(put("/api/journals/entry/j1/themes").contentType(MediaType.APPLICATION_JSON).content("{\"themes\":[]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(journalService);
    }

    @Test
    void deletesOwnedEntry() throws Exception {
        mvc.perform(delete("/api/journals/j1").header("Authorization", TOKEN))
                .andExpect(status().isNoContent());
        verify(journalService).deleteJournalEntry("j1");
    }

    @Test
    void returns404WhenDeletingAnotherUsersEntry() throws Exception {
        doThrow(new ResourceNotFoundException("Journal entry not found: bobs"))
                .when(journalService).deleteJournalEntry("bobs");

        mvc.perform(delete("/api/journals/bobs").header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedOrExpiredTokenIs401NotA500() throws Exception {
        when(jwtUtil.extractEmail("garbage")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad token"));
        when(jwtUtil.extractEmail("expired")).thenThrow(
                new io.jsonwebtoken.ExpiredJwtException(null, null, "JWT expired"));

        mvc.perform(get("/api/journals/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/journals/me").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(journalService);
    }

    @Test
    void rejectsTokenForUnknownUser() throws Exception {
        when(jwtUtil.extractEmail("ghost-token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(null);

        mvc.perform(get("/api/journals/me").header("Authorization", "Bearer ghost-token"))
                .andExpect(status().isUnauthorized());
    }
}
