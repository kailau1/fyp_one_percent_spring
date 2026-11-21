package com.example.one_percent.maintenance;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One-off jobs for when the AI provider or embedding model changes. Run one, then the app exits:
 *
 *   scripts/dev-run.sh --maintenance.task=generate-themes
 *       Embeds the ThemeDescriptions with the configured embedding model and writes
 *       src/main/resources/themes/<model>.json. Commit that file.
 *   scripts/dev-run.sh --maintenance.task=reembed
 *       Re-embeds every journal entry whose vector is missing or came from another model, so
 *       similarity and themes work for old entries too. Safe to re-run: it skips up-to-date entries.
 *
 * Only active when maintenance.task is set; a normal start never loads it.
 */
@Component
@ConditionalOnProperty("maintenance.task")
@RequiredArgsConstructor
public class MaintenanceRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceRunner.class);
    /** Entries per embedding request, and the pause between requests (free tiers limit requests per minute). */
    static final int BATCH_SIZE = 20;
    static final long PAUSE_MS = 1_500;

    private final OpenAIClient openAIClient;
    private final JournalRepository journalRepository;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String task = context.getEnvironment().getProperty("maintenance.task", "");
        int exitCode = 0;
        try {
            switch (task) {
                case "generate-themes" -> generateThemes(Path.of("src/main/resources"));
                case "reembed" -> reembedOutdatedEntries();
                default -> {
                    logger.error("Unknown maintenance.task '{}'. Use generate-themes or reembed.", task);
                    exitCode = 2;
                }
            }
        } catch (Exception e) {
            logger.error("Maintenance task '{}' failed", task, e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    /** Writes theme vectors for the configured embedding model under `resourcesDir`; returns the file. */
    public Path generateThemes(Path resourcesDir) throws IOException {
        Map<String, String> descriptions = new TreeMap<>(ThemeDescriptions.ALL);
        List<List<Double>> vectors = openAIClient.embedAll(new ArrayList<>(descriptions.values()));

        Map<String, List<Double>> themes = new TreeMap<>();
        int i = 0;
        for (String name : descriptions.keySet()) themes.put(name, vectors.get(i++));

        String model = openAIClient.settings().embeddingModel();
        Path file = resourcesDir.resolve(EmbeddingService.themesResourceFor(model));
        Files.createDirectories(file.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), themes);
        logger.info("Wrote {} theme vectors ({} numbers each) for '{}' to {}", themes.size(), vectors.get(0).size(), model, file);
        return file;
    }

    /** Re-embeds entries with no vector or one of another length; returns how many were updated. */
    public int reembedOutdatedEntries() throws IOException, InterruptedException {
        int dimensions = openAIClient.embed("dimension check").size();
        List<JournalEntry> outdated = journalRepository.findAll().stream()
                .filter(entry -> entry.getContent() != null && !entry.getContent().isBlank())
                .filter(entry -> entry.getContentEmbedding() == null || entry.getContentEmbedding().size() != dimensions)
                .toList();
        logger.info("{} entries need new {}-number embeddings from '{}'",
                outdated.size(), dimensions, openAIClient.settings().embeddingModel());

        int done = 0;
        for (int start = 0; start < outdated.size(); start += BATCH_SIZE) {
            List<JournalEntry> batch = outdated.subList(start, Math.min(start + BATCH_SIZE, outdated.size()));
            List<List<Double>> vectors = openAIClient.embedAll(batch.stream().map(JournalEntry::getContent).toList());
            for (int i = 0; i < batch.size(); i++) batch.get(i).setContentEmbedding(vectors.get(i));
            journalRepository.saveAll(batch);
            done += batch.size();
            logger.info("Re-embedded {}/{}", done, outdated.size());
            if (done < outdated.size()) Thread.sleep(PAUSE_MS);
        }
        return done;
    }
}
