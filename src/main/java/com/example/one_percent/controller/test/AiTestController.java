package com.example.one_percent.controller.test;

import com.example.one_percent.service.test.AiTestingService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/test-ai")
@RequiredArgsConstructor
public class AiTestController {

    private final AiTestingService aiTestingService;

    @PostMapping("/feedback")
    public ResponseEntity<String> getFeedback(@RequestBody Map<String, String> request) {
        String journalContent = request.get("journalContent");

        try {
            String feedback = aiTestingService.getFeedbackFromInitialPrompt(journalContent);
            return ResponseEntity.ok(feedback);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating AI feedback: " + e.getMessage());
        }
    }
}

