package com.example.one_percent.service.journal;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.mapper.JournalEntryMapper;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.service.ai.AiService;
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
import java.util.Optional;
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


    public Optional<JournalEntryDTO> getJournalById(String id) {
        return journalRepository.findById(id)
                .map(journalMapper::toDto);
    }

    public void deleteJournalEntry(String id) {
        journalRepository.deleteById(id);
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

    public JournalEntry saveAndProcessJournal(String content, String userId) throws IOException {
        JournalEntry entry = new JournalEntry();
        entry.setUserId(userId);
        entry.setContent(content);

        List<Double> contentEmbedding = embeddingService.generateEmbedding(entry.getContent());
        entry.setContentEmbedding(contentEmbedding);

        return journalRepository.save(entry);
    }

    public void addResponse(String journalId, String llmResponse) {
        Optional<JournalEntry> optionalEntry = journalRepository.findById(journalId);

        if (optionalEntry.isPresent()) {
            JournalEntry entry = optionalEntry.get();
            entry.setLlmResponse(llmResponse);

            try {
                String feedbackSummary = summaryService.getFeedbackSummary(llmResponse);
                entry.setLlmSummary(feedbackSummary);

            } catch (IOException e) {
                throw new RuntimeException("Failed to summarise or embed LLM response", e);
            }

            journalRepository.save(entry);
        } else {
            throw new IllegalArgumentException("Journal not found: " + journalId);
        }
    }

}
