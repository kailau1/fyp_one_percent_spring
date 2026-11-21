package com.example.one_percent.maintenance;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.embedding.ThemeDefinitions;
import com.example.one_percent.service.embedding.ThemeVectors;
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
import java.util.Objects;
import java.util.TreeMap;

/**
 * One-off jobs for when the AI provider, embedding model or theme definitions change. Run one, and
 * the app exits:
 *
 *   scripts/dev-run.sh --maintenance.task=generate-themes
 *       Embeds every example in themes/definitions.json with the configured embedding model, makes
 *       one vector per theme (the average of its examples), calibrates the tagging threshold, and
 *       writes src/main/resources/themes/<model>[-<dims>].json. Commit that file.
 *   scripts/dev-run.sh --maintenance.task=reembed
 *       Re-embeds (and re-tags) every entry whose vector is missing or came from another model.
 *   scripts/dev-run.sh --maintenance.task=retag
 *       Re-tags entries tagged with older theme vectors, from their stored embeddings (no AI calls).
 *
 * Safe to re-run: each task skips entries that are already up to date. Only active when
 * maintenance.task is set; a normal start never loads it.
 */
@Component
@ConditionalOnProperty("maintenance.task")
@RequiredArgsConstructor
public class MaintenanceRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceRunner.class);
    /**
     * Texts per embedding request, and the pause between requests. Free tiers limit requests per
     * minute, and Gemini counts each text in a batch as a request: 20 texts every 15s stays under 100/min.
     */
    static final int BATCH_SIZE = 20;
    long pauseMs = 15_000;
    /** If the limit is hit anyway: wait this long and try again, a few times. */
    long rateLimitWaitMs = 60_000;
    static final int RATE_LIMIT_RETRIES = 4;
    static final double SECONDARY_MARGIN = 0.02;

    private final OpenAIClient openAIClient;
    private final JournalRepository journalRepository;
    private final EmbeddingService embeddingService;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String task = context.getEnvironment().getProperty("maintenance.task", "");
        int exitCode = 0;
        try {
            switch (task) {
                case "generate-themes" -> generateThemes(Path.of("src/main/resources"));
                case "reembed" -> reembedOutdatedEntries();
                case "retag" -> retagOutdatedEntries();
                default -> {
                    logger.error("Unknown maintenance.task '{}'. Use generate-themes, reembed or retag.", task);
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

    /** What generate-themes produced, for the log and for tests. */
    public record Calibration(Path file, double everydayMax, double examplesP10, double minSimilarity) {
    }

    /**
     * Theme vectors for the configured embedding model, written under `resourcesDir`.
     *
     * Threshold calibration: an everyday entry (no theme) should score below it and a real one above.
     * "Above" is judged leave-one-out: each example is scored against its theme's average WITHOUT
     * itself, as a new entry would be. The threshold sits midway between the highest everyday score
     * and the 10th-percentile example score.
     */
    public Calibration generateThemes(Path resourcesDir) throws IOException, InterruptedException {
        ThemeDefinitions definitions = embeddingService.definitions();
        List<String> names = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (ThemeDefinitions.Theme theme : definitions.themes()) {
            for (String example : theme.examples()) {
                names.add(theme.name());
                texts.add(example);
            }
        }
        int exampleCount = texts.size();
        texts.addAll(definitions.everyday());

        List<List<Double>> vectors = new ArrayList<>();
        for (List<Double> v : embedInBatches(texts)) vectors.add(normalise(v));
        List<List<Double>> examples = vectors.subList(0, exampleCount);
        List<List<Double>> everyday = vectors.subList(exampleCount, vectors.size());

        // per theme: the sum of its example vectors (the average is the sum / count; cosine ignores scale)
        Map<String, double[]> sums = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < exampleCount; i++) {
            double[] sum = sums.computeIfAbsent(names.get(i), n -> new double[examples.get(0).size()]);
            add(sum, examples.get(i), 1);
            counts.merge(names.get(i), 1, Integer::sum);
        }

        double everydayMax = everyday.stream().mapToDouble(v -> bestScore(v, sums, null, null)).max().orElse(0);
        List<Double> heldOut = new ArrayList<>();
        for (int i = 0; i < exampleCount; i++) heldOut.add(bestScore(examples.get(i), sums, names.get(i), examples.get(i)));
        heldOut.sort(Double::compare);
        double p10 = heldOut.get(heldOut.size() / 10);
        double minSimilarity = round((everydayMax + p10) / 2);
        if (p10 <= everydayMax) {
            logger.warn("Everyday entries score as high as real examples ({} vs {}): no-theme detection will be "
                    + "unreliable; add more varied examples to themes/definitions.json", everydayMax, p10);
        }

        Map<String, List<Double>> centroids = new TreeMap<>();
        sums.forEach((name, sum) -> {
            List<Double> mean = new ArrayList<>();
            for (double x : sum) mean.add(round(x / counts.get(name), 7));
            centroids.put(name, mean);
        });

        String model = openAIClient.settings().embeddingModel();
        Integer dims = openAIClient.settings().embeddingDimensions();
        ThemeVectors themes = new ThemeVectors(model, examples.get(0).size(), definitions.hash(),
                minSimilarity, SECONDARY_MARGIN, centroids);
        Path file = resourcesDir.resolve(EmbeddingService.themesResourceFor(model, dims));
        Files.createDirectories(file.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), themes);
        logger.info("Wrote {} themes for '{}' to {}. Everyday entries score up to {}, examples (held out) "
                        + "mostly above {}: tagging threshold {}", centroids.size(), model, file,
                round(everydayMax), round(p10), minSimilarity);
        return new Calibration(file, everydayMax, p10, minSimilarity);
    }

    /** Re-embeds and re-tags entries with no vector or one of another length; returns how many were updated. */
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
            List<List<Double>> vectors = embedWithRetries(batch.stream().map(JournalEntry::getContent).toList());
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setContentEmbedding(vectors.get(i));
                embeddingService.tag(batch.get(i));
            }
            journalRepository.saveAll(batch);
            done += batch.size();
            logger.info("Re-embedded {}/{}", done, outdated.size());
            if (done < outdated.size()) Thread.sleep(pauseMs);
        }
        return done;
    }

    /** Re-tags entries whose tags came from other theme vectors, from their stored embeddings. */
    public int retagOutdatedEntries() {
        List<JournalEntry> outdated = journalRepository.findAll().stream()
                .filter(entry -> entry.getContentEmbedding() != null)
                .filter(entry -> !Objects.equals(entry.getThemesVersion(), embeddingService.themesVersion()))
                .toList();
        outdated.forEach(embeddingService::tag);
        journalRepository.saveAll(outdated);
        logger.info("Re-tagged {} entries with {}", outdated.size(), embeddingService.themesVersion());
        return outdated.size();
    }

    private List<List<Double>> embedInBatches(List<String> texts) throws IOException, InterruptedException {
        List<List<Double>> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            if (start > 0) Thread.sleep(pauseMs);
            vectors.addAll(embedWithRetries(texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()))));
            logger.info("Embedded {}/{} texts", Math.min(start + BATCH_SIZE, texts.size()), texts.size());
        }
        return vectors;
    }

    /** Embeds a batch, waiting and retrying when the AI service says the rate limit was hit (HTTP 429). */
    List<List<Double>> embedWithRetries(List<String> texts) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return openAIClient.embedAll(texts);
            } catch (IOException e) {
                boolean rateLimited = e.getMessage() != null && e.getMessage().contains("HTTP 429");
                if (!rateLimited || attempt > RATE_LIMIT_RETRIES) throw e;
                logger.info("Rate limit reached; waiting {}s before retrying ({}/{})", rateLimitWaitMs / 1000, attempt, RATE_LIMIT_RETRIES);
                Thread.sleep(rateLimitWaitMs);
            }
        }
    }

    /** The best cosine score of `v` against the themes; `heldOutTheme`'s sum has `heldOut` taken out first. */
    static double bestScore(List<Double> v, Map<String, double[]> sums, String heldOutTheme, List<Double> heldOut) {
        double best = -1;
        for (Map.Entry<String, double[]> theme : sums.entrySet()) {
            double[] centroid = theme.getValue().clone();
            if (theme.getKey().equals(heldOutTheme)) add(centroid, heldOut, -1);
            best = Math.max(best, cosine(v, centroid));
        }
        return best;
    }

    private static void add(double[] sum, List<Double> v, int sign) {
        for (int i = 0; i < sum.length; i++) sum[i] += sign * v.get(i);
    }

    static double cosine(List<Double> a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < b.length; i++) {
            dot += a.get(i) * b[i];
            na += a.get(i) * a.get(i);
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private static List<Double> normalise(List<Double> v) {
        double n = Math.sqrt(v.stream().mapToDouble(x -> x * x).sum());
        return n == 0 ? v : v.stream().map(x -> x / n).toList();
    }

    private static double round(double x) {
        return round(x, 4);
    }

    private static double round(double x, int places) {
        double f = Math.pow(10, places);
        return Math.round(x * f) / f;
    }
}
