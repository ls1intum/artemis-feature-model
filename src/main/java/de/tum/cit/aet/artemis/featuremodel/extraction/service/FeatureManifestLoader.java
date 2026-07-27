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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ExcludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.RenameEntry;

/**
 * Loads the relocatable YAML feature scope manifest and fails fast on authoring errors that are wrong regardless of
 * any Artemis checkout: malformed YAML, unknown fields, missing required values, duplicate anchors or ids, and
 * parent or group references that do not exist in the manifest itself. Problems that only a scan can reveal, such as
 * anchors no longer present in Artemis, are reported by the curation step instead of failing here.
 */
public class FeatureManifestLoader {

    private static final Set<String> ROOT_FIELDS = Set.of("manifestVersion", "verifiedAgainstArtemisCommit", "include", "exclude", "conceptualNodes",
            "constraints", "renames");

    private static final Set<String> INCLUDE_FIELDS = Set.of("anchor", "id", "group", "parent", "kind", "optionality", "category", "defaultState", "order",
            "requiresCapabilities", "providesCapabilities", "artifactMappings", "name", "description", "documentationUrl", "rationale");

    private static final Set<String> EXCLUDE_FIELDS = Set.of("anchor", "reason", "rationale");

    private static final Set<String> CONCEPTUAL_FIELDS = Set.of("id", "parent", "kind", "optionality", "category", "groupType", "order", "name", "description");

    private static final Set<String> CONSTRAINT_FIELDS = Set.of("id", "type", "source", "target", "description");

    private static final Set<String> RENAME_FIELDS = Set.of("from", "to", "rationale");

    private static final Set<String> MAPPING_FIELDS = Set.of("target", "path", "valueWhenSelected", "valueWhenDeselected", "valueFromProfile",
            "requiredWhenSelected", "secret");

    private static final Set<String> OPTIONALITY_VALUES = Set.of(FeatureScopeManifest.OPTIONALITY_MANDATORY, FeatureScopeManifest.OPTIONALITY_OPTIONAL);

    private static final Set<String> CATEGORY_VALUES = Set.of(FeatureScopeManifest.CATEGORY_FUNCTIONAL, FeatureScopeManifest.CATEGORY_TECHNICAL);

    private static final Set<String> DEFAULT_STATE_VALUES = Set.of("enabled", "disabled");

    private static final Set<String> GROUP_TYPE_VALUES = Set.of("and", "or", "alternative");

    private static final Set<String> CONSTRAINT_TYPE_VALUES = Set.of("requires", "excludes");

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
        List<ConstraintEntry> constraints = parseConstraints(root.get("constraints"));
        List<RenameEntry> renames = parseRenames(root.get("renames"));
        validateUniqueness(includes, excludes, conceptualNodes);
        validateInternalReferences(includes, conceptualNodes);
        validateConstraintReferences(constraints, includes, conceptualNodes);
        validateRenames(renames, includes, conceptualNodes);
        return new FeatureScopeManifest(manifestVersion, verifiedCommit, includes, excludes, conceptualNodes, constraints, renames);
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
                    optionalString(entry, "parent", location), optionalString(entry, "kind", location), optionality(entry, location),
                    enumeratedString(entry, "category", CATEGORY_VALUES, location), enumeratedString(entry, "defaultState", DEFAULT_STATE_VALUES, location),
                    optionalOrder(entry, location), stringList(entry, "requiresCapabilities", location), stringList(entry, "providesCapabilities", location),
                    parseMappingHints(entry.get("artifactMappings"), location), optionalString(entry, "name", location),
                    optionalString(entry, "description", location), optionalString(entry, "documentationUrl", location), optionalString(entry, "rationale", location)));
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
            String groupType = enumeratedString(entry, "groupType", GROUP_TYPE_VALUES, location);
            String kind = optionalString(entry, "kind", location);
            if (groupType != null && !"group".equals(kind)) {
                throw new FeatureManifestException(location + ".groupType is only allowed on nodes of kind 'group'.");
            }
            entries.add(new ConceptualNode(requiredString(entry, "id", location), optionalString(entry, "parent", location), kind,
                    optionality(entry, location), enumeratedString(entry, "category", CATEGORY_VALUES, location), groupType, optionalOrder(entry, location),
                    optionalString(entry, "name", location), optionalString(entry, "description", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Parses the constraints section.
     *
     * @param value raw YAML value of the constraints section, or null when absent.
     * @return parsed constraint entries in manifest order.
     * @throws FeatureManifestException if a constraint is malformed or uses an unknown type.
     */
    private List<ConstraintEntry> parseConstraints(Object value) {
        List<ConstraintEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "constraints")) {
            String location = "constraints[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, CONSTRAINT_FIELDS, location);
            String type = requiredString(entry, "type", location);
            if (!CONSTRAINT_TYPE_VALUES.contains(type)) {
                throw new FeatureManifestException(location + ".type must be one of " + CONSTRAINT_TYPE_VALUES + ".");
            }
            entries.add(new ConstraintEntry(requiredString(entry, "id", location), type, requiredString(entry, "source", location),
                    requiredString(entry, "target", location), optionalString(entry, "description", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Parses the explicit workflow rename section.
     *
     * @param value raw YAML value of the renames section, or null when absent.
     * @return parsed rename entries in declaration order.
     * @throws FeatureManifestException if a rename is malformed.
     */
    private List<RenameEntry> parseRenames(Object value) {
        List<RenameEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "renames")) {
            String location = "renames[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, RENAME_FIELDS, location);
            entries.add(new RenameEntry(requiredString(entry, "from", location), requiredString(entry, "to", location),
                    requiredString(entry, "rationale", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Parses the artifact mapping hints of an include entry.
     *
     * @param value raw YAML value of the artifactMappings field, or null when absent.
     * @param entryLocation location label of the owning entry.
     * @return parsed mapping hints in declaration order.
     * @throws FeatureManifestException if a mapping hint is malformed.
     */
    private List<FeatureScopeManifest.MappingHint> parseMappingHints(Object value, String entryLocation) {
        List<FeatureScopeManifest.MappingHint> hints = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, entryLocation + ".artifactMappings")) {
            String location = entryLocation + ".artifactMappings[" + index + "]";
            Map<String, Object> hint = asMap(item, location);
            rejectUnknownFields(hint, MAPPING_FIELDS, location);
            hints.add(new FeatureScopeManifest.MappingHint(requiredString(hint, "target", location), requiredString(hint, "path", location),
                    hint.get("valueWhenSelected"), hint.get("valueWhenDeselected"), optionalString(hint, "valueFromProfile", location),
                    optionalBoolean(hint, "requiredWhenSelected", location), optionalBoolean(hint, "secret", location)));
            index++;
        }
        return List.copyOf(hints);
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
     * Rejects constraint endpoints that do not point at a manifest-declared id.
     *
     * @param constraints parsed constraint entries.
     * @param includes parsed include entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @throws FeatureManifestException if a constraint references an undeclared feature id.
     */
    private void validateConstraintReferences(List<ConstraintEntry> constraints, List<IncludeEntry> includes, List<ConceptualNode> conceptualNodes) {
        Set<String> knownIds = new LinkedHashSet<>();
        includes.forEach(entry -> knownIds.add(entry.id()));
        conceptualNodes.forEach(node -> knownIds.add(node.id()));
        Set<String> constraintIds = new LinkedHashSet<>();
        for (ConstraintEntry constraint : constraints) {
            requireUnique(constraintIds, constraint.id(), "Duplicate constraint id '" + constraint.id() + "'.");
            requireKnownReference(knownIds, constraint.id(), constraint.source());
            requireKnownReference(knownIds, constraint.id(), constraint.target());
        }
    }

    /**
     * Rejects ambiguous, chained, self-referential, and unknown-target workflow renames.
     *
     * @param renames parsed rename entries.
     * @param includes parsed include entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @throws FeatureManifestException if a rename is unsafe or ambiguous.
     */
    private void validateRenames(List<RenameEntry> renames, List<IncludeEntry> includes, List<ConceptualNode> conceptualNodes) {
        Set<String> currentIds = new LinkedHashSet<>();
        includes.forEach(entry -> currentIds.add(entry.id()));
        conceptualNodes.forEach(node -> currentIds.add(node.id()));
        Set<String> sources = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        for (RenameEntry rename : renames) {
            if (rename.from().equals(rename.to())) {
                throw new FeatureManifestException("Rename source and target must differ: '" + rename.from() + "'.");
            }
            requireUnique(sources, rename.from(), "Duplicate rename source '" + rename.from() + "'.");
            requireUnique(targets, rename.to(), "Duplicate rename target '" + rename.to() + "'.");
            if (!currentIds.contains(rename.to())) {
                throw new FeatureManifestException("Rename target '" + rename.to() + "' is not a current manifest-declared id.");
            }
            if (currentIds.contains(rename.from())) {
                throw new FeatureManifestException("Rename source '" + rename.from() + "' is still a current manifest-declared id.");
            }
        }
    }

    /**
     * Reads and validates the optional optionality field.
     *
     * @param values parsed mapping.
     * @param location location label for failure messages.
     * @return declared optionality, or null when absent.
     * @throws FeatureManifestException if the value is not {@code mandatory} or {@code optional}.
     */
    private String optionality(Map<String, Object> values, String location) {
        String value = optionalString(values, "optionality", location);
        if (value != null && !OPTIONALITY_VALUES.contains(value)) {
            throw new FeatureManifestException(location + ".optionality must be one of " + OPTIONALITY_VALUES + ".");
        }
        return value;
    }

    /**
     * Reads an optional string field restricted to an allowed value set.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param allowed allowed values.
     * @param location location label for failure messages.
     * @return declared value, or null when absent.
     * @throws FeatureManifestException if the value is not in the allowed set.
     */
    private String enumeratedString(Map<String, Object> values, String field, Set<String> allowed, String location) {
        String value = optionalString(values, field, location);
        if (value != null && !allowed.contains(value)) {
            throw new FeatureManifestException(location + "." + field + " must be one of " + allowed + ".");
        }
        return value;
    }

    /**
     * Reads the optional relation order field.
     *
     * @param values parsed mapping.
     * @param location location label for failure messages.
     * @return declared order, or null when absent.
     * @throws FeatureManifestException if the value is not a positive integer.
     */
    private Integer optionalOrder(Map<String, Object> values, String location) {
        Object value = values.get("order");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Integer order) || order < 1) {
            throw new FeatureManifestException(location + ".order must be a positive integer when present.");
        }
        return order;
    }

    /**
     * Reads an optional boolean field.
     *
     * @param values parsed mapping.
     * @param field field name.
     * @param location location label for failure messages.
     * @return declared boolean, or null when absent.
     * @throws FeatureManifestException if the value is present but not a boolean.
     */
    private Boolean optionalBoolean(Map<String, Object> values, String field, String location) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean bool)) {
            throw new FeatureManifestException(location + "." + field + " must be a boolean when present.");
        }
        return bool;
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
