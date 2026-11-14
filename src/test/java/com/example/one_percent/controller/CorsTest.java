package com.example.one_percent.controller;

import com.example.one_percent.config.WebConfig;
import com.example.one_percent.controller.core.JournalController;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.security.SecurityConfig;
import com.example.one_percent.service.journal.JournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browsers send a "preflight" OPTIONS request before cross-origin calls; these check its answer. */
@WebMvcTest(JournalController.class)
@Import({SecurityConfig.class, WebConfig.class})
class CorsTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JournalService journalService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserRepository userRepository;

    @Test
    void webAppOriginMayCallTheApi() throws Exception {
        mvc.perform(options("/api/journals/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void unknownOriginIsRefused() throws Exception {
        mvc.perform(options("/api/journals/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
