package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.core.UserController;
import com.example.one_percent.dto.UserDTO;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.user.UserService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The profile routes, through the real security chain. */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, WebConfig.class})
class UserControllerTest {

    private static final String TOKEN = "Bearer valid-token";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;
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
    void profileNeedsAToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me/time-zone").contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"UTC\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void returnsTheProfileWithoutThePassword() throws Exception {
        when(userService.getCurrentUser()).thenReturn(
                UserDTO.builder().id("alice").email("alice@example.com").timeZone("Europe/London").build());

        mvc.perform(get("/api/users/me").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/London"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void setsTheTimeZone() throws Exception {
        when(userService.updateTimeZone("Asia/Tokyo")).thenReturn(UserDTO.builder().id("alice").timeZone("Asia/Tokyo").build());

        mvc.perform(put("/api/users/me/time-zone").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Asia/Tokyo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Tokyo"));
    }

    @Test
    void anUnknownTimeZoneIs400() throws Exception {
        when(userService.updateTimeZone("Mars/Olympus")).thenThrow(new IllegalArgumentException("unknown zone"));

        mvc.perform(put("/api/users/me/time-zone").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest());
    }
}
