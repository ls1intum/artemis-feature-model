package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Scans the English {@code featureToggles.json} i18n resource, the Artemis-owned source of user-facing names,
 * descriptions, and disable warnings for module features, runtime toggles, and displayed profiles.
 */
class FeatureI18nScan {

    private static final String SECTION_MODULES = "modules";

    private static final String SECTION_TOGGLES = "toggles";

    private static final String SECTION_PROFILES = "profiles";

    /**
     * Texts of one i18n feature entry.
     *
     * @param name display name.
     * @param description description text, or null.
     * @param disableWarning disable warning text, or null.
     */
    record FeatureTexts(String name, String description, String disableWarning) {
    }

    /**
     * Scan result of the i18n resource.
     *
     * @param file checkout-relative path of the i18n file.
     * @param moduleTexts module feature texts by module id.
     * @param toggleTexts runtime toggle texts by toggle name.
     * @param profileTexts profile texts by profile id.
     */
    record Result(String file, Map<String, FeatureTexts> moduleTexts, Map<String, FeatureTexts> toggleTexts, Map<String, FeatureTexts> profileTexts) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without texts.
         */
        static Result empty() {
            return new Result(null, Map.of(), Map.of(), Map.of());
        }
    }

    private final ObjectMapper objectMapper;

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

    /**
     * Creates the scan with the shared Jackson mapper.
     *
     * @param objectMapper Jackson mapper used to parse the i18n JSON.
     */
    FeatureI18nScan(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Scans the i18n feature texts of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned texts per section.
     * @throws IOException if the i18n file cannot be read.
     * @throws IllegalArgumentException if the i18n file cannot be located or lacks the features section.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.FEATURE_I18N, "artemisApp root",
                content -> content.contains("\"artemisApp\""));
        JsonNode root = objectMapper.readTree(source.readFile(file));
        JsonNode features = root.path("artemisApp").path("features");
        if (features.isMissingNode()) {
            throw new IllegalArgumentException("File " + file + " has no artemisApp.features section.");
        }
        return new Result(file, scanSection(features.path(SECTION_MODULES)), scanSection(features.path(SECTION_TOGGLES)), scanSection(features.path(SECTION_PROFILES)));
    }

    /**
     * Scans one i18n section into feature texts keyed by feature id.
     *
     * @param section section node, possibly missing.
     * @return texts by feature id in document order.
     */
    private Map<String, FeatureTexts> scanSection(JsonNode section) {
        Map<String, FeatureTexts> texts = new LinkedHashMap<>();
        if (!section.isObject()) {
            return texts;
        }
        section.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value.isObject() && value.has("name")) {
                texts.put(entry.getKey(), new FeatureTexts(value.path("name").asString(), textOrNull(value, "description"), textOrNull(value, "disableWarning")));
            }
        });
        return texts;
    }

    /**
     * Reads an optional text property.
     *
     * @param node entry node.
     * @param property property name.
     * @return property text, or null when absent.
     */
    private String textOrNull(JsonNode node, String property) {
        JsonNode value = node.path(property);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

}
