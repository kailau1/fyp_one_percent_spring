package com.example.one_percent.service.reflection;

import com.example.one_percent.config.AiSettings;
import com.example.one_percent.dto.WeeklyReflectionDTO;
import com.example.one_percent.exception.AiUnavailableException;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.Habit;
import com.example.one_percent.model.HabitHistory;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.model.User;
import com.example.one_percent.model.WeeklyReflection;
import com.example.one_percent.repository.HabitHistoryRepository;
import com.example.one_percent.repository.HabitRepository;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.repository.WeeklyReflectionRepository;
import com.example.one_percent.service.client.AiModel;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.support.TestAuth;
import com.example.one_percent.util.UserClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReflectionServiceTest {

    @Mock
    private JournalRepository journalRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitHistoryRepository habitHistoryRepository;
    @Mock
    private WeeklyReflectionRepository reflectionRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private OpenAIClient openAIClient;

    private ReflectionService service;
    private User alice;

    // "now": Wednesday 18 November 2026, 10:00 UTC → last finished week is Mon 9 – Sun 15 November
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 11, 9);

    @BeforeEach
    void setUp() {
        service = new ReflectionService(journalRepository, habitRepository, habitHistoryRepository, reflectionRepository,
                embeddingService, openAIClient, new UserClock(NOW));
        alice = loginWithZone("UTC");
        // the real message builders (they're plain JSON helpers)
        OpenAIClient real = new OpenAIClient(AiSettings.openAiDefaults("k", "http://ai.invalid/v1"));
        lenient().when(openAIClient.createSingleMessage(anyString(), anyString()))
                .thenAnswer(inv -> real.createSingleMessage(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(openAIClient.createMessages(anyList())).thenAnswer(inv -> real.createMessages(inv.getArgument(0)));
                lenient().when(embeddingService.effectiveThemes(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static User loginWithZone(String zone) {
        User user = TestAuth.user("alice");
        user.setTimeZone(zone);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    private static JournalEntry entry(String id, String createdAt, String content) {
        JournalEntry e = new JournalEntry();
        e.setId(id);
        e.setUserId("alice");
        e.setTitle("Title " + id);
        e.setContent(content);
        e.setCreatedAt(Instant.parse(createdAt));
        return e;
    }

    private void lastWeekHas(JournalEntry... entries) {
        when(journalRepository.findWrittenBetween(eq("alice"), eq(Instant.parse("2026-11-09T00:00:00Z")), eq(Instant.parse("2026-11-16T00:00:00Z"))))
                .thenReturn(List.of(entries));
    }

    private static OpenAIClient.Reply reply(String text) {
        return new OpenAIClient.Reply(text, "smart-model");
    }

    private static final String GOOD_REPLY = """
            {"opening": "A week of early mornings and one hard Thursday.",
             "quotes": [{"entry": 1, "text": "the run cleared my head"}, {"entry": 2, "text": "I never said this"}],
             "question": "What made Thursday heavier than the rest?"}""";

    @Test
    void writesLastWeeksReflectionOnceAndStoresIt() throws Exception {
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        lastWeekHas(entry("e1", "2026-11-10T07:00:00Z", "Up early, and the run cleared my head before work."),
                entry("e2", "2026-11-12T21:00:00Z", "Thursday was heavy. Everything felt slow."));
        when(openAIClient.sendJsonCompletion(any(), eq(AiModel.SMART), anyInt(), anyDouble())).thenReturn(reply(GOOD_REPLY));

        WeeklyReflectionDTO dto = service.latest();

        assertThat(dto.getStatus()).isEqualTo("ready");
        assertThat(dto.getWeekStart()).isEqualTo(LAST_WEEK);
        assertThat(dto.getWeekEnd()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(dto.getEntryCount()).isEqualTo(2);
        assertThat(dto.getDaysWritten()).isEqualTo(2);
        assertThat(dto.getOpening()).startsWith("A week of early mornings");
        assertThat(dto.getQuestion()).endsWith("rest?");
        // the invented quote is dropped; the real one keeps its entry
        assertThat(dto.getQuotes()).extracting(WeeklyReflection.Quote::getText).containsExactly("the run cleared my head");
        assertThat(dto.getQuotes().get(0).getEntryId()).isEqualTo("e1");
        ArgumentCaptor<WeeklyReflection> stored = ArgumentCaptor.forClass(WeeklyReflection.class);
        verify(reflectionRepository).insert(stored.capture());
        assertThat(stored.getValue().getId()).isEqualTo("alice:2026-11-09");
        assertThat(stored.getValue().getModel()).isEqualTo("smart-model");
    }

    @Test
    void aStoredReflectionIsReturnedWithoutAskingTheAi() throws Exception {
        WeeklyReflection stored = new WeeklyReflection();
        stored.setId("alice:2026-11-09");
        stored.setWeekStart(LAST_WEEK);
        stored.setOpening("Already written.");
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.of(stored));

        assertThat(service.latest().getOpening()).isEqualTo("Already written.");
        verifyNoInteractions(openAIClient, journalRepository);
    }

    @Test
    void fewerThanTwoEntriesMeansNoAiCallAndNothingStored() throws Exception {
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        lastWeekHas(entry("e1", "2026-11-10T07:00:00Z", "Just one."));

        WeeklyReflectionDTO dto = service.latest();

        assertThat(dto.getStatus()).isEqualTo("notEnoughEntries");
        assertThat(dto.getEntryCount()).isEqualTo(1);
        assertThat(dto.getOpening()).isNull();
        verify(openAIClient, never()).sendJsonCompletion(any(), any(), anyInt(), anyDouble());
        verify(reflectionRepository, never()).insert(any(WeeklyReflection.class));
    }

    @Test
    void weeksFollowTheUsersTimeZone() {
        // Monday 16 November, 01:00 in Tokyo is still Sunday 15 November, 16:00 UTC
        TestAuth.clear();
        loginWithZone("Asia/Tokyo");
        service = new ReflectionService(journalRepository, habitRepository, habitHistoryRepository, reflectionRepository,
                embeddingService, openAIClient, new UserClock(Clock.fixed(Instant.parse("2026-11-15T16:00:00Z"), ZoneOffset.UTC)));
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        when(journalRepository.findWrittenBetween(eq("alice"), any(), any())).thenReturn(List.of());

        WeeklyReflectionDTO dto = service.latest();

        assertThat(dto.getWeekStart()).isEqualTo(LAST_WEEK); // in Tokyo, the week of the 9th has just finished
        // and it's counted from Tokyo's midnight: Sunday 8 November, 15:00 UTC
        verify(journalRepository).findWrittenBetween("alice", Instant.parse("2026-11-08T15:00:00Z"), Instant.parse("2026-11-15T15:00:00Z"));
    }

    @Test
    void anUnusableReplyIsRetriedOnceThenGivesUp() throws Exception {
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        lastWeekHas(entry("e1", "2026-11-10T07:00:00Z", "One."), entry("e2", "2026-11-11T07:00:00Z", "Two."));
        when(openAIClient.sendJsonCompletion(any(), any(), anyInt(), anyDouble())).thenReturn(reply("Sorry, here you go: not json"), reply("{\"opening\": \"\"}"));

        assertThatThrownBy(() -> service.latest()).isInstanceOf(AiUnavailableException.class).hasMessageContaining("Try again");
        verify(openAIClient, times(2)).sendJsonCompletion(any(), any(), anyInt(), anyDouble());
        verify(reflectionRepository, never()).insert(any(WeeklyReflection.class));
    }

    @Test
    void anAiFailureIsRetriedAndASecondSuccessIsUsed() throws Exception {
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        lastWeekHas(entry("e1", "2026-11-10T07:00:00Z", "One."), entry("e2", "2026-11-11T07:00:00Z", "Two."));
        when(openAIClient.sendJsonCompletion(any(), any(), anyInt(), anyDouble()))
                .thenThrow(new IOException("AI chat completion failed with HTTP 429: quota"))
                .thenReturn(new OpenAIClient.Reply("```json\n{\"opening\": \"Two quiet days.\", \"quotes\": [], \"question\": \"What next?\"}\n```", "fast-model"));

        assertThat(service.latest().getOpening()).isEqualTo("Two quiet days.");
        ArgumentCaptor<WeeklyReflection> stored = ArgumentCaptor.forClass(WeeklyReflection.class);
        verify(reflectionRepository).insert(stored.capture());
        assertThat(stored.getValue().getModel()).isEqualTo("fast-model"); // the model that actually wrote it
    }

    @Test
    void ifAnotherRequestStoredItFirstThatOneIsUsed() throws Exception {
        WeeklyReflection theirs = new WeeklyReflection();
        theirs.setWeekStart(LAST_WEEK);
        theirs.setOpening("Theirs.");
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty(), Optional.of(theirs));
        lastWeekHas(entry("e1", "2026-11-10T07:00:00Z", "One."), entry("e2", "2026-11-11T07:00:00Z", "Two."));
        when(openAIClient.sendJsonCompletion(any(), any(), anyInt(), anyDouble())).thenReturn(reply(GOOD_REPLY));
        when(reflectionRepository.insert(any(WeeklyReflection.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThat(service.latest().getOpening()).isEqualTo("Theirs.");
    }

    @Test
    void countsThemesAgainstTheFourWeeksBeforeAndHabitDays() throws Exception {
        when(reflectionRepository.findById("alice:2026-11-09")).thenReturn(Optional.empty());
        JournalEntry a = entry("e1", "2026-11-10T07:00:00Z", "One.");
        JournalEntry b = entry("e2", "2026-11-10T20:00:00Z", "Two, same day.");
        lastWeekHas(a, b);
        JournalEntry earlier = entry("e0", "2026-10-20T07:00:00Z", "Before.");
        when(journalRepository.findWrittenBetween("alice", Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-11-09T00:00:00Z")))
                .thenReturn(List.of(earlier));
        when(embeddingService.effectiveThemes(a)).thenReturn(List.of("burnout"));
        when(embeddingService.effectiveThemes(b)).thenReturn(List.of("burnout", "anxiety"));
        when(embeddingService.effectiveThemes(earlier)).thenReturn(List.of("burnout"));
        Habit run = new Habit("h1", "alice", "Morning run", null, "#B4E197", false, "standard", null, null, null, null);
        Habit later = new Habit("h2", "alice", "Made later", null, "#fff", false, "standard", null, null, Instant.parse("2026-11-17T00:00:00Z"), null);
        when(habitRepository.findByUserId("alice")).thenReturn(List.of(run, later));
        when(habitHistoryRepository.findByUserIdAndDateBetweenOrderByDateDesc("alice", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 16)))
                .thenReturn(List.of(new HabitHistory("r1", "alice", "h1", LocalDate.of(2026, 11, 10), true),
                        new HabitHistory("r2", "alice", "h1", LocalDate.of(2026, 11, 11), true),
                        new HabitHistory("r3", "alice", "h1", LocalDate.of(2026, 11, 12), false)));
        when(openAIClient.sendJsonCompletion(any(), any(), anyInt(), anyDouble())).thenReturn(reply(GOOD_REPLY));

        WeeklyReflectionDTO dto = service.latest();

        assertThat(dto.getDaysWritten()).isEqualTo(1);
        assertThat(dto.getThemes()).containsExactly(
                new WeeklyReflection.ThemeCount("burnout", 2, 1),
                new WeeklyReflection.ThemeCount("anxiety", 1, 0));
        assertThat(dto.getHabits()).containsExactly(new WeeklyReflection.HabitDays("Morning run", "#B4E197", 2)); // "Made later" didn't exist yet
    }

    @Test
    void theAiIsGivenTheNumbersAndTheEntriesTrimmedToSize() {
        WeeklyReflection r = new WeeklyReflection();
        r.setWeekStart(LAST_WEEK);
        r.setEntryCount(2);
        r.setDaysWritten(2);
        r.setThemes(List.of(new WeeklyReflection.ThemeCount("burnout", 2, 0)));
        r.setHabits(List.of(new WeeklyReflection.HabitDays("Morning run", "#fff", 3)));
        String long_ = "x".repeat(3000);

        String text = service.describeWeek(r, List.of(entry("e1", "2026-11-10T07:00:00Z", long_), entry("e2", "2026-11-12T07:00:00Z", "Short.")), ZoneOffset.UTC);

        assertThat(text).contains("2 entries, on 2 of 7 days", "Theme \"burnout\": 2 entries this week, 0 in the four weeks before",
                "Habit \"Morning run\": done on 3 of 7 days", "[Entry 1, Tuesday] Title e1", "[Entry 2, Thursday] Title e2\nShort.");
        assertThat(text).doesNotContain("x".repeat(ReflectionService.MAX_CHARS_PER_ENTRY + 1));
    }

    @Test
    void quotesAreMatchedIgnoringCaseSpacingAndCurlyQuotes() {
        List<JournalEntry> entries = List.of(entry("e1", "2026-11-10T07:00:00Z", "I said \"I can't  do this\" — and then I did."));

        var written = service.parse("{\"opening\":\"o\",\"quotes\":[{\"entry\":1,\"text\":\"“I can’t do this” – and then I did\"},{\"entry\":5,\"text\":\"x\"}],\"question\":\"q\"}", entries);

        assertThat(written).isPresent();
        assertThat(written.get().quotes()).hasSize(1);
    }

    @Test
    void anotherUsersWeekIsNotFound() {
        when(reflectionRepository.findById("alice:2026-11-02")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forWeek(LocalDate.of(2026, 11, 2))).isInstanceOf(ResourceNotFoundException.class);
        verify(reflectionRepository).findById("alice:2026-11-02"); // always looked up under the current user's id
    }
}
