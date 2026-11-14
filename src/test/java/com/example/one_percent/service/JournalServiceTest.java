package com.example.one_percent.service;

import com.example.one_percent.dto.JournalEntryDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.mapper.JournalEntryMapper;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.ai.SummaryService;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    @Mock
    private JournalRepository journalRepository;
    @Mock
    private SummaryService summaryService;
    @Mock
    private EmbeddingService embeddingService;

    private final JournalEntryMapper mapper = Mappers.getMapper(JournalEntryMapper.class);
    private JournalService journalService;

    @BeforeEach
    void setUp() {
        journalService = new JournalService(journalRepository, mapper, summaryService, embeddingService);
        TestAuth.loginAs(ALICE);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private JournalEntry entry(String id, String ownerId) {
        JournalEntry e = new JournalEntry();
        e.setId(id);
        e.setUserId(ownerId);
        e.setTitle("Title " + id);
        e.setContent("Content " + id);
        return e;
    }

    /** Simulates Mongo: an entry is only found when queried with its real owner's ID. */
    private void storeInDb(JournalEntry e) {
        lenient().when(journalRepository.findByIdAndUserId(eq(e.getId()), anyString()))
                .thenAnswer(inv -> e.getUserId().equals(inv.getArgument(1)) ? Optional.of(e) : Optional.empty());
    }

    @Nested
    class Ownership {

        @Test
        void getJournalById_returnsEntryOwnedByCurrentUser() {
            storeInDb(entry("j1", ALICE));

            JournalEntryDTO dto = journalService.getJournalById("j1");

            assertThat(dto.getId()).isEqualTo("j1");
            assertThat(dto.getTitle()).isEqualTo("Title j1");
        }

        @Test
        void getJournalById_hidesEntryOwnedByAnotherUser() {
            storeInDb(entry("j2", BOB));

            assertThatThrownBy(() -> journalService.getJournalById("j2"))
                    .isInstanceOf(ResourceNotFoundException.class);
            // the unscoped lookup must never be used for by-ID reads
            verify(journalRepository, never()).findById(anyString());
        }

        @Test
        void getJournalById_throwsNotFoundForMissingEntry() {
            when(journalRepository.findByIdAndUserId("nope", ALICE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> journalService.getJournalById("nope"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void deleteJournalEntry_deletesEntryOwnedByCurrentUser() {
            JournalEntry mine = entry("j1", ALICE);
            storeInDb(mine);

            journalService.deleteJournalEntry("j1");

            verify(journalRepository).delete(mine);
        }

        @Test
        void deleteJournalEntry_refusesToDeleteAnotherUsersEntry() {
            storeInDb(entry("j2", BOB));

            assertThatThrownBy(() -> journalService.deleteJournalEntry("j2"))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(journalRepository, never()).delete(any());
            verify(journalRepository, never()).deleteById(anyString());
        }

        @Test
        void updateJournalEntry_refusesToUpdateAnotherUsersEntry() {
            storeInDb(entry("j2", BOB));
            JournalEntryDTO update = JournalEntryDTO.builder().id("j2").title("hacked").content("x").build();

            assertThatThrownBy(() -> journalService.updateJournalEntry(update))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(journalRepository, never()).save(any());
        }

        @Test
        void addResponse_refusesToAttachFeedbackToAnotherUsersEntry() throws IOException {
            storeInDb(entry("j2", BOB));

            assertThatThrownBy(() -> journalService.addResponse("j2", "feedback"))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(summaryService, never()).getFeedbackSummary(anyString());
            verify(journalRepository, never()).save(any());
        }

        @Test
        void getOwnedJournalEntry_scopesLookupToAuthenticatedUser() {
            TestAuth.loginAs(BOB);
            storeInDb(entry("j2", BOB));

            assertThat(journalService.getOwnedJournalEntry("j2").getUserId()).isEqualTo(BOB);
            verify(journalRepository).findByIdAndUserId("j2", BOB);
        }
    }

    @Test
    void getJournalsByAuthenticatedUser_onlyQueriesCurrentUsersEntries() {
        when(journalRepository.findByUserId(ALICE)).thenReturn(List.of(entry("j1", ALICE), entry("j3", ALICE)));

        List<JournalEntryDTO> result = journalService.getJournalsByAuthenticatedUser();

        assertThat(result).extracting(JournalEntryDTO::getId).containsExactly("j1", "j3");
        verify(journalRepository).findByUserId(ALICE);
    }

    @Test
    void createJournalEntry_assignsOwnerFromTokenAndIgnoresClientSuppliedId() throws IOException {
        when(embeddingService.generateEmbedding("hello")).thenReturn(List.of(0.1, 0.2));
        when(journalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        JournalEntryDTO request = JournalEntryDTO.builder().id("someone-elses-id").title("t").content("hello").build();

        journalService.createJournalEntry(request);

        ArgumentCaptor<JournalEntry> saved = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getUserId()).isEqualTo(ALICE);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getContentEmbedding()).containsExactly(0.1, 0.2);
        verify(summaryService, never()).getFeedbackSummary(anyString());
    }

    @Test
    void createJournalEntry_summarisesFeedbackWhenPresent() throws IOException {
        when(embeddingService.generateEmbedding(anyString())).thenReturn(List.of(1.0));
        when(summaryService.getFeedbackSummary("great advice")).thenReturn("summary");
        when(journalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        journalService.createJournalEntry(
                JournalEntryDTO.builder().title("t").content("c").llmResponse("great advice").build());

        ArgumentCaptor<JournalEntry> saved = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalRepository).save(saved.capture());
        assertThat(saved.getValue().getLlmSummary()).isEqualTo("summary");
    }

    @Test
    void updateJournalEntry_updatesOwnedEntry() {
        JournalEntry mine = entry("j1", ALICE);
        storeInDb(mine);
        when(journalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JournalEntryDTO result = journalService.updateJournalEntry(
                JournalEntryDTO.builder().id("j1").title("New").content("Body").llmResponse("fb").build());

        assertThat(result.getTitle()).isEqualTo("New");
        assertThat(mine.getContent()).isEqualTo("Body");
        assertThat(mine.getUserId()).isEqualTo(ALICE);
    }

    @Test
    void addResponse_storesFeedbackAndSummaryOnOwnedEntry() throws IOException {
        JournalEntry mine = entry("j1", ALICE);
        storeInDb(mine);
        when(summaryService.getFeedbackSummary("fb")).thenReturn("short");

        journalService.addResponse("j1", "fb");

        assertThat(mine.getLlmResponse()).isEqualTo("fb");
        assertThat(mine.getLlmSummary()).isEqualTo("short");
        verify(journalRepository).save(mine);
    }

    @Test
    void buildDraftEntry_embedsContentWithoutSaving() throws IOException {
        when(embeddingService.generateEmbedding("draft")).thenReturn(List.of(0.5));

        JournalEntry draft = journalService.buildDraftEntry("draft");

        assertThat(draft.getUserId()).isEqualTo(ALICE);
        assertThat(draft.getContentEmbedding()).containsExactly(0.5);
        verify(journalRepository, never()).save(any());
    }
}
