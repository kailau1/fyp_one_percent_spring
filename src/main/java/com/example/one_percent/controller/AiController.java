package com.example.one_percent.controller;

import com.example.one_percent.dto.AiRequest;
import com.example.one_percent.dto.AiResponse;
import com.example.one_percent.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin // Enable this if frontend is on a different origin
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/feedback")
    public AiResponse getAiFeedback(@RequestBody AiRequest request) throws Exception {
        String feedback = aiService.getAiFeedback(request.getJournalContent());
        return new AiResponse(feedback);
    }
}
