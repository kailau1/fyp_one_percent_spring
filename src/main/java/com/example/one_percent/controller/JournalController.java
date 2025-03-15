package com.example.one_percent.controller;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.service.JournalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/journals")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<JournalEntry>> getUserJournals(@PathVariable String userId) {
        List<JournalEntry> journals = journalService.getJournalsByUserId(userId);
        return ResponseEntity.ok(journals);
    }

    @PostMapping
    public ResponseEntity<JournalEntry> createJournal(@RequestBody JournalEntry entry) {
        JournalEntry createdEntry = journalService.createJournalEntry(entry);
        return ResponseEntity.ok(createdEntry);
    }

    @GetMapping("/entry/{id}")
    public ResponseEntity<Optional<JournalEntry>> getJournalById(@PathVariable String id) {
        Optional<JournalEntry> journal = journalService.getJournalById(id);
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
    public ResponseEntity<JournalEntry> updateJournal(@RequestBody JournalEntry entry) {
        JournalEntry updatedEntry = journalService.updateJournalEntry(entry);
        return ResponseEntity.ok(updatedEntry);
    }
}