package com.example.one_percent.service;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.mapper.JournalEntryMapper;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalRepository journalRepository;
    private final JournalEntryMapper journalMapper;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return user;
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }

    public List<JournalEntryDTO> getJournalsByAuthenticatedUser() {
        User user = getAuthenticatedUser();

        return journalRepository.findByUserId(user.getId())
                .stream()
                .map(journalMapper::toDto)
                .collect(Collectors.toList());
    }

    public JournalEntryDTO createJournalEntry(JournalEntryDTO entryDTO) {
        User user = getAuthenticatedUser();

        JournalEntry entry = journalMapper.toEntity(entryDTO);
        entry.setUserId(user.getId());
        entry.setCreatedAt(LocalDateTime.now());

        return journalMapper.toDto(journalRepository.save(entry));
    }

    public Optional<JournalEntryDTO> getJournalById(String id) {
        return journalRepository.findById(id)
                .map(journalMapper::toDto);
    }

    public void deleteJournalEntry(String id) {
        journalRepository.deleteById(id);
    }

    public void addResponse(String id, String response) {
        JournalEntry entry = journalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Journal not found with ID: " + id));
        entry.setLlmResponse(response);
        journalRepository.save(entry);
    }

    public JournalEntryDTO updateJournalEntry(JournalEntryDTO entryDTO) {
        Optional<JournalEntry> existingJournal = journalRepository.findById(entryDTO.getId());
        if (existingJournal.isPresent()) {
            JournalEntry journalToUpdate = existingJournal.get();
            journalToUpdate.setTitle(entryDTO.getTitle());
            journalToUpdate.setContent(entryDTO.getContent());
            journalToUpdate.setLlmResponse(entryDTO.getLlmResponse());

            return journalMapper.toDto(journalRepository.save(journalToUpdate));
        }
        throw new IllegalArgumentException("Journal not found with ID: " + entryDTO.getId());
    }
}
