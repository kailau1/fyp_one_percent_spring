package com.example.one_percent.controller;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
@CrossOrigin
public class JournalController {

    private final JournalService journalService;

    @GetMapping("/me")
    public ResponseEntity<List<JournalEntryDTO>> getUserJournals() {
        List<JournalEntryDTO> journals = journalService.getJournalsByAuthenticatedUser();
        return ResponseEntity.ok(journals);
    }

    @PostMapping
    public ResponseEntity<JournalEntryDTO> createJournal(@RequestBody JournalEntryDTO entryDTO) {
        JournalEntryDTO createdEntry = journalService.createJournalEntry(entryDTO);
        return ResponseEntity.ok(createdEntry);
    }

    @GetMapping("/entry/{id}")
    public ResponseEntity<Optional<JournalEntryDTO>> getJournalById(@PathVariable String id) {
        Optional<JournalEntryDTO> journal = journalService.getJournalById(id);
        return ResponseEntity.ok(journal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable String id) {
        journalService.deleteJournalEntry(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/response/{id}")
    public ResponseEntity<Void> addResponse(@PathVariable String id, @RequestBody String response) {
        journalService.addResponse(id, response);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/entry")
    public ResponseEntity<JournalEntryDTO> updateJournal(@RequestBody JournalEntryDTO entryDTO) {
        JournalEntryDTO updatedEntry = journalService.updateJournalEntry(entryDTO);
        return ResponseEntity.ok(updatedEntry);
    }
}
