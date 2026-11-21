package com.example.one_percent.controller.memory;

import com.example.one_percent.dto.AiContextDTO;
import com.example.one_percent.dto.FactRequestDTO;
import com.example.one_percent.service.memory.AiMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** "What One Percent knows about you": the logged-in user's AI context, facts and switches. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AiMemoryController {

    private final AiMemoryService aiMemoryService;

    @GetMapping("/ai-context")
    public ResponseEntity<AiContextDTO> context() {
        return ResponseEntity.ok(aiMemoryService.getContext());
    }

    /** 400 if blank, too long, or the list is full. */
    @PostMapping("/facts")
    public ResponseEntity<AiContextDTO.Fact> addFact(@RequestBody FactRequestDTO request) {
        return ResponseEntity.status(201).body(aiMemoryService.addFact(request.getText()));
    }

    /** 404 if the user has no fact with that id. */
    @DeleteMapping("/facts/{id}")
    public ResponseEntity<Void> removeFact(@PathVariable String id) {
        aiMemoryService.removeFact(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ai-context/settings")
    public ResponseEntity<AiContextDTO> updateSettings(@RequestBody AiContextDTO.Settings settings) {
        return ResponseEntity.ok(aiMemoryService.updateSettings(settings));
    }
}
