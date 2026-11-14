package com.example.one_percent.controller.core;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.service.journal.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
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
    public ResponseEntity<JournalEntryDTO> getJournalById(@PathVariable String id) {
        return ResponseEntity.ok(journalService.getJournalById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable String id) {
        journalService.deleteJournalEntry(id);
        return ResponseEntity.noContent().build();
    }


    @PutMapping("/entry")
    public ResponseEntity<JournalEntryDTO> updateJournal(@RequestBody JournalEntryDTO entryDTO) {
        JournalEntryDTO updatedEntry = journalService.updateJournalEntry(entryDTO);
        return ResponseEntity.ok(updatedEntry);
    }
}
