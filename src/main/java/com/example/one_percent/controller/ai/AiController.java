package com.example.one_percent.controller.ai;

import com.example.one_percent.dto.AiRequestDTO;
import com.example.one_percent.dto.AiResponseDTO;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.security.CurrentUser;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.service.tip.TipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final JournalService journalService;
    private final TipService tipService;

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

    /** One tip per calendar day (the user's own day), shared by everyone and generated once. */
    @GetMapping("/tip")
    public AiResponseDTO getTipOfTheDay() throws Exception {
        return new AiResponseDTO(tipService.getTipOfTheDay(CurrentUser.get()));
    }


}
