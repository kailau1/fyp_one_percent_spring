package com.example.one_percent.service.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * What the themes are: src/main/resources/themes/definitions.json. Each theme is defined by example
 * entries, not by its name: an entry is matched to the themes whose examples it most resembles.
 * `everyday` entries have no theme; they calibrate the score below which an entry gets none.
 *
 * Edit the file, then regenerate the vectors: scripts/dev-run.sh --maintenance.task=generate-themes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThemeDefinitions(List<Theme> themes, List<String> everyday) {

    public static final String RESOURCE = "themes/definitions.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Theme(String name, String meaning, List<String> examples) {
    }

    public static ThemeDefinitions load() {
        try (InputStream is = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ObjectMapper().readValue(is, ThemeDefinitions.class);
        } catch (IOException e) {
            // ships inside the app, so a failure here is a packaging bug, not a runtime condition
            throw new IllegalStateException("Couldn't read " + RESOURCE, e);
        }
    }

    public List<String> names() {
        return themes.stream().map(Theme::name).sorted().toList();
    }

    /**
     * A short fingerprint of the themes and examples. Stored with the generated vectors, so a vectors
     * file made from older definitions is noticed (and tags made with older vectors are refreshed).
     */
    public String hash() {
        try {
            byte[] json = new ObjectMapper().writeValueAsString(this).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
