package com.example.one_percent.controller.ai;

import com.example.one_percent.dto.AiRequestDTO;
import com.example.one_percent.dto.AiResponseDTO;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.service.journal.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final JournalService journalService;

    @PostMapping("/feedback")
    public AiResponseDTO getAiFeedback(@RequestBody AiRequestDTO request) throws Exception {
        String journalId = request.getJournalId();
        String feedback = aiService.getAiFeedback(journalId, request.getJournalContent());

        // Only saved entries can have feedback attached. Drafts get their feedback stored
        // when the app creates the entry (it sends llmResponse with the create request).
        if (journalId != null && !journalId.isBlank()) {
            journalService.addResponse(journalId, feedback);
        }
        return new AiResponseDTO(feedback);
    }

    @GetMapping("/tip")
    public AiResponseDTO getTipOfTheDay() throws Exception {
        String tip = aiService.getDailyTip();
        return new AiResponseDTO(tip);
    }


}
