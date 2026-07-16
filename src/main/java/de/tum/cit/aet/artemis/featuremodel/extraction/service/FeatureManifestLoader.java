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

/**
 * Loads the relocatable YAML feature scope manifest and fails fast on authoring errors that are wrong regardless of
 * any Artemis checkout: malformed YAML, unknown fields, missing required values, duplicate anchors or ids, and
 * parent or group references that do not exist in the manifest itself. Problems that only a scan can reveal, such as
 * anchors no longer present in Artemis, are reported by the curation step instead of failing here.
 */
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
     * @throws FeatureManifestException if the YAML shape, required fields, or internal references are invalid.
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
     * @throws FeatureManifestException if the YAML shape, required fields, or internal references are invalid.
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
        validateUniqueness(includes, excludes, conceptualNodes);
        validateInternalReferences(includes, conceptualNodes);
        return new FeatureScopeManifest(manifestVersion, verifiedCommit, includes, excludes, conceptualNodes);
    }

    /**
     * Parses the include section.
     *
     * @param value raw YAML value of the include section, or null when absent.
     * @return parsed include entries in manifest order.
     * @throws FeatureManifestException if an entry is malformed.
     */
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

    /**
     * Parses the exclude section.
     *
     * @param value raw YAML value of the exclude section, or null when absent.
     * @return parsed exclude entries in manifest order.
     * @throws FeatureManifestException if an entry is malformed or lacks its mandatory reason.
     */
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

    /**
     * Parses the conceptualNodes section.
     *
     * @param value raw YAML value of the conceptualNodes section, or null when absent.
     * @return parsed conceptual nodes in manifest order.
     * @throws FeatureManifestException if a node is malformed.
     */
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

    /**
     * Rejects duplicate anchors across the include and exclude sections and duplicate curated ids across the include
     * and conceptualNodes sections.
     *
     * @param includes parsed include entries.
     * @param excludes parsed exclude entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @throws FeatureManifestException if a duplicate is found.
     */
    private void validateUniqueness(List<IncludeEntry> includes, List<ExcludeEntry> excludes, List<ConceptualNode> conceptualNodes) {
        Set<String> anchors = new LinkedHashSet<>();
        for (IncludeEntry entry : includes) {
            requireUnique(anchors, entry.anchor(), "Duplicate manifest anchor '" + entry.anchor() + "'.");
        }
        for (ExcludeEntry entry : excludes) {
            requireUnique(anchors, entry.anchor(), "Duplicate manifest anchor '" + entry.anchor() + "'.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (IncludeEntry entry : includes) {
            requireUnique(ids, entry.id(), "Duplicate curated id '" + entry.id() + "' across include and conceptualNodes.");
        }
        for (ConceptualNode node : conceptualNodes) {
            requireUnique(ids, node.id(), "Duplicate curated id '" + node.id() + "' across include and conceptualNodes.");
        }
    }

    /**
     * Rejects parent and group references that do not point at another manifest-declared id. This is a static check:
     * the referenced id universe is fully known from the manifest alone.
     *
     * @param includes parsed include entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @throws FeatureManifestException if a reference points at an undeclared id.
     */
    private void validateInternalReferences(List<IncludeEntry> includes, List<ConceptualNode> conceptualNodes) {
        Set<String> knownIds = new LinkedHashSet<>();
        includes.forEach(entry -> knownIds.add(entry.id()));
        conceptualNodes.forEach(node -> knownIds.add(node.id()));
        for (ConceptualNode node : conceptualNodes) {
            requireKnownReference(knownIds, node.id(), node.parent());
        }
        for (IncludeEntry entry : includes) {
            requireKnownReference(knownIds, entry.id(), entry.parent());
            requireKnownReference(knownIds, entry.id(), entry.group());
        }
    }

    /**
     * Adds a value to a uniqueness set or fails with the given message.
     *
     * @param values previously seen values.
     * @param value value to add.
     * @param message failure message used when the value was already present.
     * @throws FeatureManifestException if the value is a duplicate.
     */
    private void requireUnique(Set<String> values, String value, String message) {
        if (!values.add(value)) {
            throw new FeatureManifestException(message);
        }
    }

    /**
     * Requires that an optional parent or group reference points at a manifest-declared id.
     *
     * @param knownIds all ids declared by the manifest.
     * @param id id of the referencing entry, used in the failure message.
     * @param reference referenced parent or group id, or null when not set.
     * @throws FeatureManifestException if the reference is unknown.
     */
    private void requireKnownReference(Set<String> knownIds, String id, String reference) {
        if (reference != null && !knownIds.contains(reference)) {
            throw new FeatureManifestException("Manifest entry '" + id + "' references undeclared parent/group '" + reference + "'.");
        }
    }

    /**
     * Casts a YAML value to a string-keyed mapping.
     *
     * @param value raw YAML value.
     * @param location location label for failure messages.
     * @return string-keyed mapping.
     * @throws FeatureManifestException if the value is not a mapping with string field names.
     */
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

    /**
     * Casts an optional YAML value to a list.
     *
     * @param value raw YAML value, or null when absent.
     * @param location location label for failure messages.
     * @return list value; empty when absent.
     * @throws FeatureManifestException if the value is present but not a list.
     */
    private List<?> asList(Object value, String location) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new FeatureManifestException(location + " must be a YAML list.");
        }
        return list;
    }

    /**
     * Rejects fields outside the allowed set so typos cannot silently drop curation data.
     *
     * @param values parsed mapping.
     * @param allowed allowed field names.
     * @param location location label for failure messages.
     * @throws FeatureManifestException if an unknown field is present.
     */
    private void rejectUnknownFields(Map<String, Object> values, Set<String> allowed, String location) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new FeatureManifestException(location + " contains unknown field(s): " + String.join(", ", unknown) + ".");
        }
    }

    /**
     * Reads a required integer field.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param location location label for failure messages.
     * @return integer value.
     * @throws FeatureManifestException if the field is absent or not an integer.
     */
    private int requiredInteger(Map<String, Object> values, String field, String location) {
        Object value = values.get(field);
        if (!(value instanceof Integer integer)) {
            throw new FeatureManifestException(location + "." + field + " must be an integer.");
        }
        return integer;
    }

    /**
     * Reads a required non-blank string field.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param location location label for failure messages.
     * @return string value.
     * @throws FeatureManifestException if the field is absent, blank, or not a string.
     */
    private String requiredString(Map<String, Object> values, String field, String location) {
        String value = optionalString(values, field, location);
        if (value == null) {
            throw new FeatureManifestException(location + "." + field + " must be a non-blank string.");
        }
        return value;
    }

    /**
     * Reads an optional string field, requiring it to be non-blank when present.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param location location label for failure messages.
     * @return string value, or null when the field is absent.
     * @throws FeatureManifestException if the field is present but blank or not a string.
     */
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

    /**
     * Reads an optional list of non-blank strings.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param location location label for failure messages.
     * @return string list; empty when the field is absent.
     * @throws FeatureManifestException if the field is present but contains non-string or blank items.
     */
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
