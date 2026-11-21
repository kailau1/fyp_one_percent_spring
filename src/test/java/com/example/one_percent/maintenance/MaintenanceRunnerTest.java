package com.example.one_percent.maintenance;

import com.example.one_percent.config.AiSettings;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRunnerTest {

    @Mock
    private OpenAIClient openAIClient;
    @Mock
    private JournalRepository journalRepository;
    @Mock
    private ConfigurableApplicationContext context;

    private MaintenanceRunner runner;

    @BeforeEach
    void setUp() {
        lenient().when(openAIClient.settings())
                .thenReturn(new AiSettings("k", "http://ai.invalid/v1", "s", "f", "gemini-embedding-001", null, null, null));
        runner = new MaintenanceRunner(openAIClient, journalRepository, context);
    }

    private static List<Double> vector(int size, double value) {
        return new ArrayList<>(Collections.nCopies(size, value));
    }

    private static JournalEntry entry(String id, String content, List<Double> embedding) {
        JournalEntry e = new JournalEntry();
        e.setId(id);
        e.setContent(content);
        e.setContentEmbedding(embedding);
        return e;
    }

    @Test
    void generateThemesWritesOneVectorPerThemeForTheConfiguredModel(@TempDir Path resources) throws Exception {
        when(openAIClient.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<List<Double>> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) vectors.add(vector(4, i));
            return vectors;
        });

        Path file = runner.generateThemes(resources);

        assertThat(file).isEqualTo(resources.resolve("themes/gemini-embedding-001.json"));
        Map<String, List<Double>> themes = new ObjectMapper().readValue(file.toFile(), new TypeReference<>() {
        });
        assertThat(themes.keySet()).isEqualTo(ThemeDescriptions.ALL.keySet());
        assertThat(themes.values()).allSatisfy(v -> assertThat(v).hasSize(4));
        // each theme gets the vector of its own description
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> sent = ArgumentCaptor.forClass(List.class);
        verify(openAIClient).embedAll(sent.capture());
        int burnout = sent.getValue().indexOf(ThemeDescriptions.ALL.get("burnout"));
        assertThat(themes.get("burnout")).containsOnly((double) burnout);
    }

    @Test
    void reembedUpdatesOnlyEntriesWithMissingOrOtherModelVectors() throws Exception {
        when(openAIClient.embed("dimension check")).thenReturn(vector(3, 0));
        JournalEntry current = entry("ok", "fine", vector(3, 0.5));
        JournalEntry oldModel = entry("old", "from openai", vector(1536, 0.1));
        JournalEntry missing = entry("none", "never embedded", null);
        JournalEntry empty = entry("blank", "  ", null); // nothing to embed
        when(journalRepository.findAll()).thenReturn(List.of(current, oldModel, missing, empty));
        when(openAIClient.embedAll(List.of("from openai", "never embedded"))).thenReturn(List.of(vector(3, 1), vector(3, 2)));

        int updated = runner.reembedOutdatedEntries();

        assertThat(updated).isEqualTo(2);
        assertThat(oldModel.getContentEmbedding()).isEqualTo(vector(3, 1));
        assertThat(missing.getContentEmbedding()).isEqualTo(vector(3, 2));
        assertThat(current.getContentEmbedding()).isEqualTo(vector(3, 0.5));
        verify(journalRepository).saveAll(List.of(oldModel, missing));
    }

    @Test
    void reembedSendsLargeJournalsInBatches() throws Exception {
        when(openAIClient.embed("dimension check")).thenReturn(vector(2, 0));
        List<JournalEntry> entries = new ArrayList<>();
        for (int i = 0; i < MaintenanceRunner.BATCH_SIZE + 5; i++) entries.add(entry("e" + i, "text " + i, null));
        when(journalRepository.findAll()).thenReturn(entries);
        when(openAIClient.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> vector(2, 1)).toList();
        });

        assertThat(runner.reembedOutdatedEntries()).isEqualTo(MaintenanceRunner.BATCH_SIZE + 5);
        verify(openAIClient, times(2)).embedAll(anyList());
        verify(journalRepository, times(2)).saveAll(any());
    }

    @Test
    void nothingToDoWhenEveryEntryIsCurrent() throws Exception {
        when(openAIClient.embed("dimension check")).thenReturn(vector(2, 0));
        when(journalRepository.findAll()).thenReturn(List.of(entry("a", "x", vector(2, 1))));

        assertThat(runner.reembedOutdatedEntries()).isZero();
        verify(openAIClient, never()).embedAll(anyList());
        verify(journalRepository, never()).saveAll(any());
    }
}
