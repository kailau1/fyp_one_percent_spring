package com.example.one_percent.service.journal;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.mapper.JournalEntryMapper;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.ai.SummaryService;
import com.example.one_percent.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalRepository journalRepository;
    private final JournalEntryMapper journalMapper;
    private final SummaryService summaryService;
    private final EmbeddingService embeddingService;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return user;
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal);
        }
    }

    /**
     * Fetches a journal entry by ID, but only if it belongs to the authenticated user.
     * Entries owned by someone else are treated exactly like missing ones (404), so callers
     * can't probe for the existence of other users' entries.
     */
    public JournalEntry getOwnedJournalEntry(String id) {
        User user = getAuthenticatedUser();
        return journalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
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
        entry.setId(null); // never let the client choose (or overwrite) an ID on create
        entry.setUserId(user.getId());
        entry.setCreatedAt(LocalDateTime.now());

        try {

            entry.setContentEmbedding(embeddingService.generateEmbedding(entry.getContent()));

            if (entry.getLlmResponse() != null && !entry.getLlmResponse().isBlank()) {
                String llmSummary = summaryService.getFeedbackSummary(entry.getLlmResponse());
                entry.setLlmSummary(llmSummary);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to enrich journal entry with summaries or embeddings", e);
        }

        return journalMapper.toDto(journalRepository.save(entry));
    }

    public JournalEntryDTO getJournalById(String id) {
        return journalMapper.toDto(getOwnedJournalEntry(id));
    }

    public void deleteJournalEntry(String id) {
        journalRepository.delete(getOwnedJournalEntry(id));
    }

    public JournalEntryDTO updateJournalEntry(JournalEntryDTO entryDTO) {
        JournalEntry journalToUpdate = getOwnedJournalEntry(entryDTO.getId());
        journalToUpdate.setTitle(entryDTO.getTitle());
        journalToUpdate.setContent(entryDTO.getContent());
        journalToUpdate.setLlmResponse(entryDTO.getLlmResponse());

        return journalMapper.toDto(journalRepository.save(journalToUpdate));
    }

    /**
     * Builds an in-memory (unsaved) entry for the authenticated user so AI feedback can be
     * generated for a draft that hasn't been saved yet. It isn't persisted: the app saves the
     * entry (with its feedback) itself when the user taps "Done". Persisting here used to leave
     * an untitled duplicate behind for every draft that asked for feedback.
     */
    public JournalEntry buildDraftEntry(String content) throws IOException {
        JournalEntry entry = new JournalEntry();
        entry.setUserId(getAuthenticatedUser().getId());
        entry.setContent(content);
        entry.setContentEmbedding(embeddingService.generateEmbedding(content));
        return entry;
    }

    public void addResponse(String journalId, String llmResponse) {
        JournalEntry entry = getOwnedJournalEntry(journalId);
        entry.setLlmResponse(llmResponse);

        try {
            entry.setLlmSummary(summaryService.getFeedbackSummary(llmResponse));
        } catch (IOException e) {
            throw new RuntimeException("Failed to summarise LLM response", e);
        }

        journalRepository.save(entry);
    }

}
