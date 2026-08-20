package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration defaults persisted under {@code scan/config-defaults.json}. The {@code errors} field remains in this
 * artifact for byte compatibility of the persisted scan contract; orchestration collects the same diagnostics
 * through the source-scan result envelope.
 *
 * @param occurrencesByKey occurrences per dotted key; per key ordered by preferred file, then sorted path.
 * @param errors per-file YAML parse diagnostics retained in the existing JSON contract.
 */
public record ExtractedConfigurationDefaults(Map<String, List<ExtractedConfigurationDefault>> occurrencesByKey, List<ReportItem> errors) {

    /** Preserves key and occurrence order while making the persisted fact set immutable. */
    public ExtractedConfigurationDefaults {
        Map<String, List<ExtractedConfigurationDefault>> occurrences = new LinkedHashMap<>();
        if (occurrencesByKey != null) {
            occurrencesByKey.forEach((key, values) -> occurrences.put(key, List.copyOf(values)));
        }
        occurrencesByKey = Collections.unmodifiableMap(occurrences);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates an empty configuration-default set for a failed whole scanner.
     *
     * @return empty defaults.
     */
    public static ExtractedConfigurationDefaults empty() {
        return new ExtractedConfigurationDefaults(Map.of(), List.of());
    }

    /**
     * Returns the preferred occurrence of one configuration key.
     *
     * @param key dotted configuration key.
     * @return preferred occurrence, or null when the key was not scanned.
     */
    public ExtractedConfigurationDefault preferredOccurrence(String key) {
        List<ExtractedConfigurationDefault> occurrences = occurrencesByKey.get(key);
        return occurrences == null || occurrences.isEmpty() ? null : occurrences.getFirst();
    }
}
