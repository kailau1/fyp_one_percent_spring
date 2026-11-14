package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.ai.PromptController;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.ai.PromptService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
@Import({SecurityConfig.class, WebConfig.class})
class PromptControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PromptService promptService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void authenticateAlice() throws Exception {
        User alice = TestAuth.user("alice");
        when(jwtUtil.extractEmail("valid-token")).thenReturn(alice.getEmail());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(alice);
        when(promptService.getPromptSuggestions("alice")).thenReturn(List.of("What went well?"));
    }

    @Test
    void promptsForTheLoggedInUser() throws Exception {
        mvc.perform(get("/api/ai/prompt").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("What went well?"));
    }

    @Test
    void legacyRouteStillWorksWithYourOwnId() throws Exception {
        mvc.perform(get("/api/ai/prompt/alice").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("What went well?"));
    }

    @Test
    void legacyRouteRefusesSomeoneElsesId() throws Exception {
        mvc.perform(get("/api/ai/prompt/bob").header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
        verify(promptService, never()).getPromptSuggestions("bob");
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/ai/prompt")).andExpect(status().isUnauthorized());
    }
}
