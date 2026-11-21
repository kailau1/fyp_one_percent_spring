package com.example.one_percent.service.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Theme vectors generated for one embedding model (themes/<model>[-<dims>].json): one vector per
 * theme (the average of its example entries) plus the calibrated tagging thresholds.
 *
 * @param definitionsHash  ThemeDefinitions.hash() they were made from
 * @param minSimilarity    an entry needs at least this score with a theme to be tagged with it
 * @param secondaryMargin  further themes must be within this of the best score
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThemeVectors(
        String model,
        Integer dimensions,
        String definitionsHash,
        Double minSimilarity,
        Double secondaryMargin,
        Map<String, List<Double>> themes) {

    /**
     * Older files (from before calibration) are a plain {theme: vector} map. They have no thresholds,
     * so entries get their three closest themes, as they always did.
     */
    public boolean isLegacy() {
        return minSimilarity == null;
    }

    public static ThemeVectors read(InputStream is) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(is);
        if (root.has("themes") && root.get("themes").isObject()) {
            return mapper.treeToValue(root, ThemeVectors.class);
        }
        Map<String, List<Double>> flat = mapper.convertValue(root, new TypeReference<>() {
        });
        return new ThemeVectors(null, null, null, null, null, flat);
    }
}
