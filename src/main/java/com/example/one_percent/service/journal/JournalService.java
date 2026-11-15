package com.example.one_percent.service.journal;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.dto.PageDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.mapper.JournalEntryMapper;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.ai.SummaryService;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalService {

    private static final Logger logger = LoggerFactory.getLogger(JournalService.class);

    private final JournalRepository journalRepository;
    private final JournalEntryMapper journalMapper;
    private final SummaryService summaryService;
    private final EmbeddingService embeddingService;
    private final UserClock userClock;

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

    /** The most entries one page may hold. */
    public static final int MAX_PAGE_SIZE = 100;
    /** Longest search text accepted: plenty for a phrase, and keeps a regex scan cheap. */
    public static final int MAX_QUERY_LENGTH = 200;
    /** Widest range GET /api/journals/days answers in one request (two years). */
    public static final int MAX_DAYS_RANGE = 731;

    /**
     * One page of the user's entries, newest first, optionally only those whose title or text
     * contains `query` (case-insensitive).
     */
    public PageDTO<JournalEntryDTO> searchJournals(String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("'page' can't be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("'size' must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search text can be at most " + MAX_QUERY_LENGTH + " characters.");
        }
        User user = getAuthenticatedUser();
        return PageDTO.of(journalRepository.searchByUser(user.getId(), query, PageRequest.of(page, size)), journalMapper::toDto);
    }

    /**
     * The days (in the user's time zone) on which they wrote at least one entry, between `from`
     * and `to` inclusive, oldest first. Defaults to the last year. Lets a calendar or streak view
     * show writing activity without downloading every entry.
     */
    public List<LocalDate> getDaysWithEntries(LocalDate from, LocalDate to) {
        User user = getAuthenticatedUser();
        ZoneId zone = userClock.zoneOf(user);
        if ((from != null && (from.getYear() < 1900 || from.getYear() > 9999))
                || (to != null && (to.getYear() < 1900 || to.getYear() > 9999))) {
            throw new IllegalArgumentException("Dates must be between the years 1900 and 9999.");
        }
        LocalDate end = to != null ? to : userClock.today(user);
        LocalDate start = from != null ? from : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' must not be after 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS_RANGE) {
            throw new IllegalArgumentException("Ask for at most " + MAX_DAYS_RANGE + " days at a time.");
        }
        return journalRepository
                .findCreatedAtBetween(user.getId(), start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant())
                .stream()
                .map(instant -> LocalDate.ofInstant(instant, zone))
                .distinct()
                .sorted()
                .toList();
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
        entry.setCreatedAt(userClock.now());

        // The embedding and summary are extras for the AI features. If OpenAI is unreachable (or the
        // API key is wrong) the entry is still saved: a journal must never refuse to keep someone's
        // writing because a third party is down.
        try {
            entry.setContentEmbedding(embeddingService.generateEmbedding(entry.getContent()));
        } catch (IOException | RuntimeException e) {
            logger.warn("Saving journal entry without an embedding: {}", e.getMessage());
        }

        if (entry.getLlmResponse() != null && !entry.getLlmResponse().isBlank()) {
            try {
                entry.setLlmSummary(summaryService.getFeedbackSummary(entry.getLlmResponse()));
            } catch (IOException | RuntimeException e) {
                logger.warn("Saving journal entry without a feedback summary: {}", e.getMessage());
            }
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
        boolean contentChanged = !java.util.Objects.equals(journalToUpdate.getContent(), entryDTO.getContent());
        journalToUpdate.setTitle(entryDTO.getTitle());
        journalToUpdate.setContent(entryDTO.getContent());
        journalToUpdate.setLlmResponse(entryDTO.getLlmResponse());

        // Re-embed edited text so themes and "similar past entries" follow what the entry now says.
        // Best effort, as on create: if OpenAI is unavailable the edit is still saved, and the stale
        // embedding is dropped rather than kept describing text that's gone.
        if (contentChanged && (entryDTO.getContent() == null || entryDTO.getContent().isBlank())) {
            journalToUpdate.setContentEmbedding(null); // nothing to describe, and no point paying OpenAI to say so
        } else if (contentChanged) {
            try {
                journalToUpdate.setContentEmbedding(embeddingService.generateEmbedding(journalToUpdate.getContent()));
            } catch (IOException | RuntimeException e) {
                logger.warn("Saving edited journal entry without an embedding: {}", e.getMessage());
                journalToUpdate.setContentEmbedding(null);
            }
        }

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
