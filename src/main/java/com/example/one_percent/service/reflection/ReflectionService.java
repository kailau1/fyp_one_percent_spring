package com.example.one_percent.service.reflection;

import com.example.one_percent.dto.PageDTO;
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
import com.example.one_percent.security.CurrentUser;
import com.example.one_percent.service.client.AiModel;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.util.UserClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The weekly reflection: the user's own finished week (Monday to Sunday, in their time zone) read
 * back to them. Written the first time it's opened, with one AI request, then kept: it never
 * spends AI quota on someone who doesn't look, and never twice on the same week.
 *
 * The numbers (entries, days, themes, habits) are counted here; the AI only writes the words, and
 * every quote it picks is checked against the entries, so it can't put words in the user's mouth.
 */
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private static final Logger logger = LoggerFactory.getLogger(ReflectionService.class);

    static final int MIN_ENTRIES = 2;
    static final int MAX_CHARS_PER_ENTRY = 1500;
    static final int MAX_CHARS_IN_TOTAL = 8000;
    static final int MAX_QUOTES = 3;

    static final String SYSTEM_PROMPT = """
            You write a short weekly read-back for someone's private journal. You reflect their own week back to them.
            You don't coach, give advice, diagnose, or praise. Speak to them as "you", warmly and plainly, in British English.

            Return only a JSON object: {"opening": "...", "quotes": [{"entry": <entry number>, "text": "..."}], "question": "..."}
            - opening: two or three sentences on what this week held for them, drawing on the entries and the numbers given. No advice.
            - quotes: one to three short phrases (at most 25 words each) copied exactly, word for word, from the entries,
              that capture the week. Never paraphrase or join phrases together. "entry" is the entry's number.
            - question: one open question for them to carry into next week, in their own terms. Not a yes/no question.
            """;

    private final JournalRepository journalRepository;
    private final HabitRepository habitRepository;
    private final HabitHistoryRepository habitHistoryRepository;
    private final WeeklyReflectionRepository reflectionRepository;
    private final EmbeddingService embeddingService;
    private final OpenAIClient openAIClient;
    private final UserClock userClock;
    private final ObjectMapper mapper = new ObjectMapper();

    /** The Monday of the last finished week, where the user is. */
    LocalDate lastFinishedWeek(User user) {
        return userClock.today(user).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    /** Last week's reflection, written now if this is the first time it's asked for. */
    public WeeklyReflectionDTO latest() {
        User user = CurrentUser.get();
        LocalDate weekStart = lastFinishedWeek(user);
        Optional<WeeklyReflection> stored = reflectionRepository.findById(WeeklyReflection.idFor(user.getId(), weekStart));
        if (stored.isPresent()) return WeeklyReflectionDTO.of(stored.get());

        ZoneId zone = userClock.zoneOf(user);
        List<JournalEntry> entries = journalRepository.findWrittenBetween(user.getId(), start(weekStart, zone), start(weekStart.plusWeeks(1), zone));
        if (entries.size() < MIN_ENTRIES) return WeeklyReflectionDTO.notEnoughEntries(weekStart, entries.size());

        WeeklyReflection reflection = write(user, weekStart, zone, entries);
        try {
            reflectionRepository.insert(reflection);
            return WeeklyReflectionDTO.of(reflection);
        } catch (DuplicateKeyException alreadyWritten) {
            logger.debug("Reflection {} was written by a concurrent request; using that one", reflection.getId());
            return reflectionRepository.findById(reflection.getId()).map(WeeklyReflectionDTO::of).orElse(WeeklyReflectionDTO.of(reflection));
        }
    }

    /** One stored week of the current user's, or 404 (another user's weeks are never found). */
    public WeeklyReflectionDTO forWeek(LocalDate weekStart) {
        return reflectionRepository.findById(WeeklyReflection.idFor(CurrentUser.get().getId(), weekStart))
                .map(WeeklyReflectionDTO::of)
                .orElseThrow(() -> new ResourceNotFoundException("No reflection for the week of " + weekStart));
    }

    /** The current user's reflections, newest week first. */
    public PageDTO<WeeklyReflectionDTO> history(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page must be ≥ 0 and size 1–50.");
        return PageDTO.of(reflectionRepository.findByUserIdOrderByWeekStartDesc(CurrentUser.get().getId(), PageRequest.of(page, size)),
                WeeklyReflectionDTO::of);
    }

    private static Instant start(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    // ---- writing a reflection ----

    WeeklyReflection write(User user, LocalDate weekStart, ZoneId zone, List<JournalEntry> entries) {
        WeeklyReflection r = new WeeklyReflection();
        r.setId(WeeklyReflection.idFor(user.getId(), weekStart));
        r.setUserId(user.getId());
        r.setWeekStart(weekStart);
        r.setEntryIds(entries.stream().map(JournalEntry::getId).toList());
        r.setEntryCount(entries.size());
        r.setDaysWritten((int) entries.stream().map(e -> LocalDate.ofInstant(e.getCreatedAt(), zone)).distinct().count());
        r.setThemes(themeCounts(user, weekStart, zone, entries));
        r.setHabits(habitDays(user, weekStart));
        r.setCreatedAt(userClock.now());

        Written written = askAi(r, entries, zone);
        r.setOpening(written.opening());
        r.setQuotes(written.quotes());
        r.setQuestion(written.question());
        r.setModel(written.model());
        return r;
    }

    private List<WeeklyReflection.ThemeCount> themeCounts(User user, LocalDate weekStart, ZoneId zone, List<JournalEntry> week) {
        Map<String, Integer> thisWeek = countThemes(week);
        List<JournalEntry> before = journalRepository.findWrittenBetween(user.getId(), start(weekStart.minusWeeks(4), zone), start(weekStart, zone));
        Map<String, Integer> previous = countThemes(before);
        return thisWeek.entrySet().stream()
                .map(e -> new WeeklyReflection.ThemeCount(e.getKey(), e.getValue(), previous.getOrDefault(e.getKey(), 0)))
                .sorted(Comparator.comparingInt(WeeklyReflection.ThemeCount::getThisWeek).reversed().thenComparing(WeeklyReflection.ThemeCount::getName))
                .toList();
    }

    private Map<String, Integer> countThemes(List<JournalEntry> entries) {
        Map<String, Integer> counts = new HashMap<>();
        for (JournalEntry entry : entries) {
            for (String theme : embeddingService.effectiveThemes(entry)) counts.merge(theme, 1, Integer::sum);
        }
        return counts;
    }

    private List<WeeklyReflection.HabitDays> habitDays(User user, LocalDate weekStart) {
        // "Between" excludes both ends, hence the day before Monday and the day after Sunday
        List<HabitHistory> history = habitHistoryRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                user.getId(), weekStart.minusDays(1), weekStart.plusDays(7));
        Map<String, Long> done = history.stream().filter(HabitHistory::isCompleted)
                .collect(Collectors.groupingBy(HabitHistory::getHabitId, Collectors.mapping(HabitHistory::getDate, Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));
        Instant weekEnd = start(weekStart.plusWeeks(1), userClock.zoneOf(user));
        return habitRepository.findByUserId(user.getId()).stream()
                .filter(h -> h.getCreatedAt() == null || h.getCreatedAt().isBefore(weekEnd)) // existed that week
                .map(h -> new WeeklyReflection.HabitDays(habitName(h), h.getColour(), done.getOrDefault(h.getId(), 0L).intValue()))
                .sorted(Comparator.comparingInt(WeeklyReflection.HabitDays::getDaysDone).reversed())
                .toList();
    }

    static String habitName(Habit habit) {
        if ("trigger-action".equalsIgnoreCase(habit.getHabitType())) {
            return "When " + habit.getTrigger() + ", " + habit.getAction();
        }
        return habit.getHabitName();
    }

    record Written(String opening, List<WeeklyReflection.Quote> quotes, String question, String model) {
    }

    /** One AI request, retried once if the reply can't be used. */
    private Written askAi(WeeklyReflection r, List<JournalEntry> entries, ZoneId zone) {
        ArrayNode messages = openAIClient.createMessages(List.of(
                openAIClient.createSingleMessage("system", SYSTEM_PROMPT),
                openAIClient.createSingleMessage("user", describeWeek(r, entries, zone))));
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                OpenAIClient.Reply answer = openAIClient.sendJsonCompletion(messages, AiModel.SMART, 700, 0.7);
                String reply = answer.text();
                Optional<Written> written = parse(reply, entries);
                if (written.isPresent()) return withModel(written.get(), answer.model());
                last = new IOException("unusable reply: " + reply.substring(0, Math.min(200, reply.length())));
            } catch (IOException e) {
                last = e;
            }
            logger.warn("Weekly reflection attempt {} failed: {}", attempt, last.getMessage());
        }
        throw new AiUnavailableException("Your weekly reflection couldn't be written just now. Try again in a minute.", last);
    }

    private static Written withModel(Written w, String model) {
        return new Written(w.opening(), w.quotes(), w.question(), model);
    }

    String describeWeek(WeeklyReflection r, List<JournalEntry> entries, ZoneId zone) {
        StringBuilder text = new StringBuilder("The week of " + r.getWeekStart() + ".\n\nNumbers:\n");
        text.append("- ").append(r.getEntryCount()).append(" entries, on ").append(r.getDaysWritten()).append(" of 7 days.\n");
        for (WeeklyReflection.ThemeCount t : r.getThemes()) {
            text.append("- Theme \"").append(t.getName()).append("\": ").append(t.getThisWeek())
                    .append(" entries this week, ").append(t.getPreviousFourWeeks()).append(" in the four weeks before.\n");
        }
        for (WeeklyReflection.HabitDays h : r.getHabits()) {
            text.append("- Habit \"").append(h.getName()).append("\": done on ").append(h.getDaysDone()).append(" of 7 days.\n");
        }
        text.append("\nEntries:\n");
        int budget = MAX_CHARS_IN_TOTAL;
        for (int i = 0; i < entries.size() && budget > 0; i++) {
            JournalEntry e = entries.get(i);
            String content = Optional.ofNullable(e.getContent()).orElse("");
            content = content.substring(0, Math.min(content.length(), Math.min(MAX_CHARS_PER_ENTRY, budget)));
            budget -= content.length();
            String day = LocalDate.ofInstant(e.getCreatedAt(), zone).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK);
            text.append("\n[Entry ").append(i + 1).append(", ").append(day).append("] ")
                    .append(Optional.ofNullable(e.getTitle()).filter(t -> !t.isBlank()).orElse("(untitled)")).append("\n")
                    .append(content).append("\n");
        }
        return text.toString();
    }

    /** The reply as a reflection, or empty if it isn't usable. Quotes not found in their entry are dropped. */
    Optional<Written> parse(String reply, List<JournalEntry> entries) {
        try {
            String json = reply.strip().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
            JsonNode node = mapper.readTree(json);
            String opening = node.path("opening").asText("").strip();
            String question = node.path("question").asText("").strip();
            if (opening.isEmpty() || question.isEmpty()) return Optional.empty();

            List<WeeklyReflection.Quote> quotes = new ArrayList<>();
            for (JsonNode q : node.path("quotes")) {
                int n = q.path("entry").asInt(0);
                String text = q.path("text").asText("").strip().replaceAll("^[\"“‘']+|[\"”’']+$", "").strip();
                if (n < 1 || n > entries.size() || text.isEmpty() || quotes.size() >= MAX_QUOTES) continue;
                JournalEntry entry = entries.get(n - 1);
                if (!normalise(entry.getContent()).contains(normalise(text))) {
                    logger.debug("Dropping a quote not found in entry {}: {}", n, text);
                    continue;
                }
                quotes.add(new WeeklyReflection.Quote(entry.getId(), entry.getTitle(), text));
            }
            return Optional.of(new Written(opening, quotes, question, null));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** For comparing quotes: case, spacing, curly quotes and dashes don't matter. */
    static String normalise(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[‘’`]", "'").replaceAll("[“”]", "\"").replaceAll("[–—]", "-")
                .replaceAll("\\s+", " ").strip();
    }
}
