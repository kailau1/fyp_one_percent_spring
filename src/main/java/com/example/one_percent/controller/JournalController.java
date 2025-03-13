package com.example.one_percent.controller;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.service.JournalService;
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
    public List<JournalEntry> getUserJournals(@PathVariable String userId) {
        return journalService.getJournalsByUserId(userId);
    }

    @PostMapping
    public JournalEntry createJournal(@RequestBody JournalEntry entry) {
        return journalService.createJournalEntry(entry);
    }

    @GetMapping("/entry/{id}")
    public Optional<JournalEntry> getJournalById(@PathVariable String id) {
        return journalService.getJournalById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteJournal(@PathVariable String id) {
        journalService.deleteJournalEntry(id);
    }

    @PostMapping("/response/{id}")
    public void addResponse(@PathVariable String id, @RequestBody String response) {
        journalService.addResponse(id, response);
    }

    @PutMapping("/entry")
    public void updateJournal(@RequestBody JournalEntry entry) {
        journalService.updateJournalEntry(entry);
    }
}
