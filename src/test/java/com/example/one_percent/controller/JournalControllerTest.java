package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.core.JournalController;
import com.example.one_percent.dto.JournalEntryDTO;
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

import static org.mockito.Mockito.*;
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
        mvc.perform(get("/api/journals/entry/j1")).andExpect(status().isForbidden());
        verifyNoInteractions(journalService);
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
    void rejectsTokenForUnknownUser() throws Exception {
        when(jwtUtil.extractEmail("ghost-token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(null);

        mvc.perform(get("/api/journals/me").header("Authorization", "Bearer ghost-token"))
                .andExpect(status().isForbidden());
    }
}
