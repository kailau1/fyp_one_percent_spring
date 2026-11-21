package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.memory.AiMemoryController;
import com.example.one_percent.dto.AiContextDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.memory.AiMemoryService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "What One Percent knows about you", through the real security chain. */
@WebMvcTest(AiMemoryController.class)
@Import({SecurityConfig.class, WebConfig.class})
class AiMemoryControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AiMemoryService aiMemoryService;
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
    void everyRouteNeedsAToken() throws Exception {
        mvc.perform(get("/api/users/me/ai-context")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/facts").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/me/facts/f1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me/ai-context/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aiMemoryService);
    }

    @Test
    void returnsTheContext() throws Exception {
        when(aiMemoryService.getContext()).thenReturn(new AiContextDTO(List.of("Read"), List.of("burnout"),
                List.of(new AiContextDTO.Fact("f1", "I work nights", Instant.parse("2026-11-01T00:00:00Z"))),
                new AiContextDTO.Settings(true, false, true, true), "Context for the assistant:", 20, 200));

        mvc.perform(get("/api/users/me/ai-context").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habits[0]").value("Read"))
                .andExpect(jsonPath("$.facts[0].text").value("I work nights"))
                .andExpect(jsonPath("$.facts[0].createdAt").value("2026-11-01T00:00:00Z"))
                .andExpect(jsonPath("$.settings.useThemes").value(false))
                .andExpect(jsonPath("$.settings.usePastAdvice").value(true))
                .andExpect(jsonPath("$.preview").value("Context for the assistant:"))
                .andExpect(jsonPath("$.maxFacts").value(20));
    }

    @Test
    void addsAFact() throws Exception {
        when(aiMemoryService.addFact("I work nights"))
                .thenReturn(new AiContextDTO.Fact("f1", "I work nights", Instant.parse("2026-11-21T10:00:00Z")));

        mvc.perform(post("/api/users/me/facts").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"I work nights\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("f1"));
    }

    @Test
    void aRefusedFactIsA400WithTheReason() throws Exception {
        when(aiMemoryService.addFact(" ")).thenThrow(new IllegalArgumentException("Write something to add."));

        mvc.perform(post("/api/users/me/facts").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("Write something to add."));
    }

    @Test
    void deletesAFactOr404s() throws Exception {
        mvc.perform(delete("/api/users/me/facts/f1").header("Authorization", TOKEN)).andExpect(status().isNoContent());
        verify(aiMemoryService).removeFact("f1");

        doThrow(new ResourceNotFoundException("No such fact: f2")).when(aiMemoryService).removeFact("f2");
        mvc.perform(delete("/api/users/me/facts/f2").header("Authorization", TOKEN)).andExpect(status().isNotFound());
    }

    @Test
    void updatesTheSwitches() throws Exception {
        when(aiMemoryService.updateSettings(new AiContextDTO.Settings(true, false, true, false)))
                .thenReturn(new AiContextDTO(List.of(), List.of(), List.of(), new AiContextDTO.Settings(true, false, true, false), "", 20, 200));

        mvc.perform(put("/api/users/me/ai-context/settings").header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useHabits\":true,\"useThemes\":false,\"useFacts\":true,\"usePastAdvice\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.usePastAdvice").value(false));
    }
}
