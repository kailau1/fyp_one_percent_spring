package com.example.one_percent.controller;

import com.example.one_percent.dto.AiRequestDTO;
import com.example.one_percent.dto.AiResponseDTO;
import com.example.one_percent.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiController {

    private final AiService aiService;

    @Autowired
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/feedback")
    public AiResponseDTO getAiFeedback(@RequestBody AiRequestDTO request) throws Exception {
        String feedback = aiService.getAiFeedback(request.getJournalContent());
        return new AiResponseDTO(feedback);
    }

    @GetMapping("/tip")
    public AiResponseDTO getTipOfTheDay() throws Exception {
        String tip = aiService.getDailyTip();
        return new AiResponseDTO(tip);
    }

    @PostMapping("/prompt")
    public List<String> generatePrompts(@RequestBody(required = false) Map<String, Object> payload) throws Exception {
        String userContext = "";
        if (payload != null && payload.containsKey("context")) {
            userContext = (String) payload.get("context");
        }

        return aiService.getPromptSuggestions(userContext);
    }
}
