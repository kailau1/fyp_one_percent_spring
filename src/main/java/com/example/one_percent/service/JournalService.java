package com.example.one_percent.service;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class JournalService {

    private final JournalRepository journalRepository;

    public JournalService(JournalRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    public List<JournalEntry> getJournalsByUserId(String userId) {
        return journalRepository.findByUserId(userId);
    }

    public JournalEntry createJournalEntry(JournalEntry entry) {
        entry.setCreatedAt(LocalDateTime.now());
        return journalRepository.save(entry);
    }

    public Optional<JournalEntry> getJournalById(String id) {
        return journalRepository.findById(id);
    }

    public void deleteJournalEntry(String id) {
        journalRepository.deleteById(id);
    }

    public void addResponse(String id, String response) {
        JournalEntry entry = journalRepository.findById(id).get();
        entry.setLlmResponse(response);
        journalRepository.save(entry);
    }

    public void updateJournalEntry(JournalEntry entry) {
        journalRepository.save(entry);
    }
}
