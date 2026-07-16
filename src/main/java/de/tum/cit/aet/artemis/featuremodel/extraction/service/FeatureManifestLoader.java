package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ExcludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;

/** Loads the relocatable YAML feature scope manifest and validates its schema-level invariants. */
public class FeatureManifestLoader {

    private static final Set<String> ROOT_FIELDS = Set.of("manifestVersion", "verifiedAgainstArtemisCommit", "include", "exclude", "conceptualNodes");

    private static final Set<String> INCLUDE_FIELDS = Set.of("anchor", "id", "group", "parent", "kind", "requiresCapabilities", "providesCapabilities", "name",
            "description", "documentationUrl", "rationale");

    private static final Set<String> EXCLUDE_FIELDS = Set.of("anchor", "reason", "rationale");

    private static final Set<String> CONCEPTUAL_FIELDS = Set.of("id", "parent", "kind", "name", "description");

    /**
     * Loads a manifest from a filesystem path.
     *
     * @param manifestPath manifest file path.
     * @return parsed and schema-validated manifest.
     * @throws IOException if the file cannot be read.
     * @throws FeatureManifestException if the YAML shape or required fields are invalid.
     */
    public FeatureScopeManifest load(Path manifestPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            return load(inputStream, manifestPath.toString());
        }
    }

    /**
     * Loads a manifest from a stream.
     *
     * @param inputStream YAML input.
     * @param sourceLabel source label used in validation messages.
     * @return parsed and schema-validated manifest.
     * @throws FeatureManifestException if the YAML shape or required fields are invalid.
     */
    public FeatureScopeManifest load(InputStream inputStream, String sourceLabel) {
        Object loaded;
        try {
            loaded = new Yaml().load(inputStream);
        }
        catch (RuntimeException e) {
            throw new FeatureManifestException("Could not parse feature manifest " + sourceLabel + ": " + e.getMessage());
        }
        Map<String, Object> root = asMap(loaded, "Manifest root");
        rejectUnknownFields(root, ROOT_FIELDS, "manifest root");
        int manifestVersion = requiredInteger(root, "manifestVersion", "manifest root");
        if (manifestVersion != FeatureScopeManifest.CURRENT_VERSION) {
            throw new FeatureManifestException("Unsupported manifestVersion " + manifestVersion + "; expected " + FeatureScopeManifest.CURRENT_VERSION + ".");
        }
        String verifiedCommit = requiredString(root, "verifiedAgainstArtemisCommit", "manifest root");
        List<IncludeEntry> includes = parseIncludes(root.get("include"));
        List<ExcludeEntry> excludes = parseExcludes(root.get("exclude"));
        List<ConceptualNode> conceptualNodes = parseConceptualNodes(root.get("conceptualNodes"));
        validateSchemaDuplicates(includes, excludes, conceptualNodes);
        return new FeatureScopeManifest(manifestVersion, verifiedCommit, includes, excludes, conceptualNodes);
    }

    private List<IncludeEntry> parseIncludes(Object value) {
        List<IncludeEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "include")) {
            String location = "include[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, INCLUDE_FIELDS, location);
            entries.add(new IncludeEntry(requiredString(entry, "anchor", location), requiredString(entry, "id", location), optionalString(entry, "group", location),
                    optionalString(entry, "parent", location), optionalString(entry, "kind", location), stringList(entry, "requiresCapabilities", location),
                    stringList(entry, "providesCapabilities", location), optionalString(entry, "name", location), optionalString(entry, "description", location),
                    optionalString(entry, "documentationUrl", location), optionalString(entry, "rationale", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    private List<ExcludeEntry> parseExcludes(Object value) {
        List<ExcludeEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "exclude")) {
            String location = "exclude[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, EXCLUDE_FIELDS, location);
            entries.add(new ExcludeEntry(requiredString(entry, "anchor", location), requiredString(entry, "reason", location), optionalString(entry, "rationale", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    private List<ConceptualNode> parseConceptualNodes(Object value) {
        List<ConceptualNode> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "conceptualNodes")) {
            String location = "conceptualNodes[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, CONCEPTUAL_FIELDS, location);
            entries.add(new ConceptualNode(requiredString(entry, "id", location), optionalString(entry, "parent", location), optionalString(entry, "kind", location),
                    optionalString(entry, "name", location), optionalString(entry, "description", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    private void validateSchemaDuplicates(List<IncludeEntry> includes, List<ExcludeEntry> excludes, List<ConceptualNode> conceptualNodes) {
        Set<String> anchors = new LinkedHashSet<>();
        for (IncludeEntry entry : includes) {
            addUnique(anchors, entry.anchor(), "Duplicate manifest anchor '", "'.");
        }
        for (ExcludeEntry entry : excludes) {
            addUnique(anchors, entry.anchor(), "Duplicate manifest anchor '", "'.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (IncludeEntry entry : includes) {
            addUnique(ids, entry.id(), "Duplicate curated id '", "' across include and conceptualNodes.");
        }
        for (ConceptualNode node : conceptualNodes) {
            addUnique(ids, node.id(), "Duplicate curated id '", "' across include and conceptualNodes.");
        }
    }

    private void addUnique(Set<String> values, String value, String prefix, String suffix) {
        if (!values.add(value)) {
            throw new FeatureManifestException(prefix + value + suffix);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String location) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new FeatureManifestException(location + " must be a YAML mapping.");
        }
        for (Object key : raw.keySet()) {
            if (!(key instanceof String)) {
                throw new FeatureManifestException(location + " contains a non-string field name.");
            }
        }
        return (Map<String, Object>) raw;
    }

    private List<?> asList(Object value, String location) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new FeatureManifestException(location + " must be a YAML list.");
        }
        return list;
    }

    private void rejectUnknownFields(Map<String, Object> values, Set<String> allowed, String location) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new FeatureManifestException(location + " contains unknown field(s): " + String.join(", ", unknown) + ".");
        }
    }

    private int requiredInteger(Map<String, Object> values, String field, String location) {
        Object value = values.get(field);
        if (!(value instanceof Integer integer)) {
            throw new FeatureManifestException(location + "." + field + " must be an integer.");
        }
        return integer;
    }

    private String requiredString(Map<String, Object> values, String field, String location) {
        String value = optionalString(values, field, location);
        if (value == null) {
            throw new FeatureManifestException(location + "." + field + " must be a non-blank string.");
        }
        return value;
    }

    private String optionalString(Map<String, Object> values, String field, String location) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string) || string.isBlank()) {
            throw new FeatureManifestException(location + "." + field + " must be a non-blank string when present.");
        }
        return string;
    }

    private List<String> stringList(Map<String, Object> values, String field, String location) {
        List<String> strings = new ArrayList<>();
        for (Object item : asList(values.get(field), location + "." + field)) {
            if (!(item instanceof String string) || string.isBlank()) {
                throw new FeatureManifestException(location + "." + field + " must contain only non-blank strings.");
            }
            strings.add(string);
        }
        return List.copyOf(strings);
    }
}
