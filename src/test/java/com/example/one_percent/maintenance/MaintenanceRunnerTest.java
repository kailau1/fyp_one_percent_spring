package com.example.one_percent.maintenance;

import com.example.one_percent.config.AiSettings;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.embedding.ThemeDefinitions;
import com.example.one_percent.service.embedding.ThemeVectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.FileInputStream;
import java.io.InputStream;
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

    private EmbeddingService embeddingService;
    private MaintenanceRunner runner;

    @BeforeEach
    void setUp() {
        lenient().when(openAIClient.settings())
                .thenReturn(new AiSettings("k", "http://ai.invalid/v1", "s", "f", "gemini-embedding-2", 768, null, null));
        embeddingService = new EmbeddingService(journalRepository, openAIClient); // real definitions, no vectors loaded
        runner = new MaintenanceRunner(openAIClient, journalRepository, embeddingService, context);
        runner.pauseMs = 0; // no real waiting in tests
        runner.rateLimitWaitMs = 0;
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

    private static Map<String, List<Double>> threeThemes() {
        return Map.of("anxiety", List.of(1.0, 0.0, 0.0), "burnout", List.of(0.0, 1.0, 0.0), "gratitude", List.of(0.0, 0.0, 1.0));
    }

    /**
     * A fake embedding model: each theme's examples point along that theme's own axis (with a little
     * variety), everyday entries along a shared "nothing in particular" axis.
     */
    private void fakeEmbeddingModel() throws Exception {
        ThemeDefinitions definitions = embeddingService.definitions();
        List<String> names = definitions.names();
        int dims = names.size() + 1;
        when(openAIClient.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<List<Double>> vectors = new ArrayList<>();
            for (String text : texts) {
                List<Double> v = vector(dims, 0.0);
                v.set(dims - 1, 0.3);
                for (ThemeDefinitions.Theme theme : definitions.themes()) {
                    if (theme.examples().contains(text)) {
                        int axis = names.indexOf(theme.name());
                        v.set(axis, 1.0);
                        v.set((axis + 1) % names.size(), 0.1 * (text.length() % 3)); // some variety
                    }
                }
                if (definitions.everyday().contains(text)) v.set(dims - 1, 1.0);
                vectors.add(v);
            }
            return vectors;
        });
    }

    @Test
    void generateThemesWritesAVectorPerThemeAndCalibratesTheThreshold(@TempDir Path resources) throws Exception {
        fakeEmbeddingModel();

        MaintenanceRunner.Calibration result = runner.generateThemes(resources);

        // file named for the model and vector size
        assertThat(result.file()).isEqualTo(resources.resolve("themes/gemini-embedding-2-768.json"));
        ThemeVectors written;
        try (InputStream is = new FileInputStream(result.file().toFile())) {
            written = ThemeVectors.read(is);
        }
        assertThat(written.themes().keySet()).containsExactlyInAnyOrderElementsOf(embeddingService.definitions().names());
        assertThat(written.definitionsHash()).isEqualTo(embeddingService.definitions().hash());
        assertThat(written.secondaryMargin()).isEqualTo(MaintenanceRunner.SECONDARY_MARGIN);
        // the threshold sits between "everyday" scores and (held-out) example scores
        assertThat(result.everydayMax()).isLessThan(result.examplesP10());
        assertThat(written.minSimilarity()).isEqualTo(result.minSimilarity())
                .isGreaterThan(result.everydayMax()).isLessThan(result.examplesP10());
    }

    @Test
    void generatedVectorsTagEntriesLikeTheExamplesAndLeaveEverydayOnesUntagged(@TempDir Path resources) throws Exception {
        fakeEmbeddingModel();
        Path file = runner.generateThemes(resources).file();
        try (InputStream is = new FileInputStream(file.toFile())) {
            embeddingService.useVectors(ThemeVectors.read(is), "generated");
        }
        List<String> names = embeddingService.definitions().names();

        List<Double> aboutBurnout = vector(names.size() + 1, 0.0);
        aboutBurnout.set(names.indexOf("burnout"), 1.0);
        aboutBurnout.set(names.size(), 0.3);
        List<Double> everyday = vector(names.size() + 1, 0.0);
        everyday.set(names.size(), 1.0);

        assertThat(embeddingService.tagThemes(aboutBurnout).keySet()).containsExactly("burnout");
        assertThat(embeddingService.tagThemes(everyday)).isEmpty();
    }

    @Test
    void reembedUpdatesAndRetagsOnlyEntriesWithMissingOrOtherModelVectors() throws Exception {
        embeddingService.useVectors(new ThemeVectors("m", 3, "h", 0.5, 0.02, threeThemes()), "v");
        when(openAIClient.embed("dimension check")).thenReturn(vector(3, 0));
        JournalEntry current = entry("ok", "fine", vector(3, 0.5));
        JournalEntry oldModel = entry("old", "from openai", vector(1536, 0.1));
        JournalEntry missing = entry("none", "never embedded", null);
        JournalEntry empty = entry("blank", "  ", null); // nothing to embed
        when(journalRepository.findAll()).thenReturn(List.of(current, oldModel, missing, empty));
        when(openAIClient.embedAll(List.of("from openai", "never embedded")))
                .thenReturn(List.of(List.of(1.0, 0.0, 0.0), List.of(0.0, 0.0, 1.0)));

        int updated = runner.reembedOutdatedEntries();

        assertThat(updated).isEqualTo(2);
        assertThat(oldModel.getContentEmbedding()).isEqualTo(List.of(1.0, 0.0, 0.0));
        assertThat(oldModel.getThemeScores()).containsOnlyKeys("anxiety");
        assertThat(missing.getThemeScores()).containsOnlyKeys("gratitude");
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
    void waitsAndRetriesWhenTheRateLimitIsHit() throws Exception {
        when(openAIClient.embedAll(List.of("a")))
                .thenThrow(new java.io.IOException("AI embedding failed with HTTP 429: quota"))
                .thenReturn(List.of(vector(2, 1)));

        assertThat(runner.embedWithRetries(List.of("a"))).containsExactly(vector(2, 1));
    }

    @Test
    void otherFailuresAreNotRetried() throws Exception {
        when(openAIClient.embedAll(List.of("a"))).thenThrow(new java.io.IOException("AI embedding failed with HTTP 400: bad model"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runner.embedWithRetries(List.of("a"))).hasMessageContaining("400");
        verify(openAIClient, times(1)).embedAll(List.of("a"));
    }

    @Test
    void retagRefreshesOnlyEntriesTaggedWithOtherVectorsAndCallsNoAi() throws Exception {
        embeddingService.useVectors(new ThemeVectors("m", 3, "h", 0.5, 0.02, threeThemes()), "v");
        JournalEntry stale = entry("stale", "x", List.of(0.0, 1.0, 0.0));
        stale.setThemesVersion("old:abc");
        JournalEntry current = entry("current", "y", List.of(1.0, 0.0, 0.0));
        embeddingService.tag(current);
        JournalEntry noEmbedding = entry("none", "z", null);
        when(journalRepository.findAll()).thenReturn(List.of(stale, current, noEmbedding));

        assertThat(runner.retagOutdatedEntries()).isEqualTo(1);
        assertThat(stale.getThemeScores()).containsOnlyKeys("burnout");
        assertThat(stale.getThemesVersion()).isEqualTo(embeddingService.themesVersion());
        verify(journalRepository).saveAll(List.of(stale));
        verify(openAIClient, never()).embedAll(anyList());
    }
}
