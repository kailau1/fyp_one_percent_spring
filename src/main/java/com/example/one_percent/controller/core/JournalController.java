package com.example.one_percent.controller.core;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.dto.PageDTO;
import com.example.one_percent.service.journal.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    /** Every entry at once. Kept for the mobile app; new clients should page with GET /api/journals. */
    @GetMapping("/me")
    public ResponseEntity<List<JournalEntryDTO>> getUserJournals() {
        List<JournalEntryDTO> journals = journalService.getJournalsByAuthenticatedUser();
        return ResponseEntity.ok(journals);
    }

    /** One page of entries, newest first: ?q=text to search titles and text, ?page=0&size=20. */
    @GetMapping
    public ResponseEntity<PageDTO<JournalEntryDTO>> searchJournals(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(journalService.searchJournals(q, page, size));
    }

    /** Days ("YYYY-MM-DD", the user's time zone) with at least one entry: ?from=&to=, default the last year. */
    @GetMapping("/days")
    public ResponseEntity<List<LocalDate>> getDaysWithEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(journalService.getDaysWithEntries(from, to));
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
