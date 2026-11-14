package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.ai.AiController;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@Import({SecurityConfig.class, WebConfig.class})
class AiControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AiService aiService;
    @MockBean
    private JournalService journalService;
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
    void feedbackForSavedEntryIsAttachedToThatEntry() throws Exception {
        when(aiService.getAiFeedback("j1", "text")).thenReturn("nice work");

        mvc.perform(post("/api/ai/feedback").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journalId\":\"j1\",\"journalContent\":\"text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("nice work"));

        verify(journalService).addResponse("j1", "nice work");
    }

    @Test
    void feedbackForUnsavedDraftDoesNotTryToAttachToAnEntry() throws Exception {
        // The app sends journalId "" (and a userId, which must be ignored) for a new draft.
        when(aiService.getAiFeedback("", "draft")).thenReturn("fb");

        mvc.perform(post("/api/ai/feedback").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journalId\":\"\",\"journalContent\":\"draft\",\"userId\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("fb"));

        verify(journalService, never()).addResponse(anyString(), anyString());
    }

    @Test
    void feedbackOnAnotherUsersEntryIs404() throws Exception {
        when(aiService.getAiFeedback("bobs", null)).thenThrow(new ResourceNotFoundException("not found"));

        mvc.perform(post("/api/ai/feedback").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journalId\":\"bobs\"}"))
                .andExpect(status().isNotFound());

        verify(journalService, never()).addResponse(anyString(), anyString());
    }

    @Test
    void feedbackWithNothingToReviewIs400() throws Exception {
        when(aiService.getAiFeedback(null, null)).thenThrow(new IllegalArgumentException("Either journalId or content must be provided."));

        mvc.perform(post("/api/ai/feedback").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tipRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ai/tip")).andExpect(status().isForbidden());
        verifyNoInteractions(aiService);
    }
}
