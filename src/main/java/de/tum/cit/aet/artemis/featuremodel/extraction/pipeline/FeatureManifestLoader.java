package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.FeatureEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IgnoredRelationEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.NotModeledEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.TechnicalEntry;

/**
 * Loads the relocatable YAML feature scope manifest and fails fast on authoring errors that are wrong regardless of
 * any Artemis checkout: malformed YAML, unknown fields, retired sections and fields, missing required values,
 * duplicate anchors or ids, and parent, group, or constraint references that do not exist in the manifest itself.
 * Problems that only a scan can reveal, such as anchors no longer present in Artemis, are reported by the curation
 * step instead of failing here.
 */
public class FeatureManifestLoader {

    private static final Set<String> ROOT_FIELDS = Set.of("manifestVersion", "features", "technical", "notModeled", "conceptualNodes", "constraints",
            "ignoredRelations");

    private static final Set<String> FEATURE_FIELDS = Set.of("id", "group", "parent", "optionality", "category", "defaultState", "order", "configuration", "name",
            "description", "documentationUrl", "rationale");

    private static final Set<String> TECHNICAL_FIELDS = Set.of("anchor", "id", "group", "parent", "optionality", "defaultState", "order", "profiles",
            "configuration", "name", "description", "documentationUrl", "rationale");

    private static final Set<String> NOT_MODELED_FIELDS = Set.of("anchor", "reason", "rationale");

    private static final Set<String> CONCEPTUAL_FIELDS = Set.of("id", "parent", "kind", "optionality", "category", "groupType", "order", "name", "description");

    private static final Set<String> CONSTRAINT_FIELDS = Set.of("id", "type", "source", "target", "description");

    private static final Set<String> IGNORED_RELATION_FIELDS = Set.of("id", "rationale");

    private static final Set<String> CONFIGURATION_FIELDS = Set.of("key", "secret", "action");

    private static final Set<String> CONFIGURATION_ACTION_VALUES = Set.of(FeatureScopeManifest.CONFIGURATION_ACTION_INCLUDE,
            FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE);

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
        rejectRetiredFields(root);
        rejectUnknownFields(root, ROOT_FIELDS, "manifest root");
        int manifestVersion = requiredInteger(root, "manifestVersion", "manifest root");
        if (manifestVersion != FeatureScopeManifest.CURRENT_VERSION) {
            throw new FeatureManifestException("Unsupported manifestVersion " + manifestVersion + "; expected " + FeatureScopeManifest.CURRENT_VERSION
                    + ". Since manifestVersion 5 a 'features' entry keyed by the Artemis module id declares membership and semantics, technical entries derive "
                    + "their mappings from the anchor, and capabilities, alternative-group exclusions, and the root node are derived.");
        }
        List<FeatureEntry> features = parseFeatures(root.get("features"));
        List<TechnicalEntry> technical = parseTechnical(root.get("technical"));
        List<NotModeledEntry> notModeled = parseNotModeled(root.get("notModeled"));
        List<ConceptualNode> conceptualNodes = parseConceptualNodes(root.get("conceptualNodes"));
        List<ConstraintEntry> constraints = parseConstraints(root.get("constraints"));
        List<IgnoredRelationEntry> ignoredRelations = parseIgnoredRelations(root.get("ignoredRelations"));
        Set<String> knownIds = validateUniqueness(features, technical, notModeled, conceptualNodes);
        validateInternalReferences(features, technical, conceptualNodes, knownIds);
        validateConstraintReferences(constraints, knownIds);
        return new FeatureScopeManifest(manifestVersion, features, technical, notModeled, conceptualNodes, constraints, ignoredRelations);
    }

    /**
     * Rejects the sections manifest versions 4 and 5 replaced and the identity fields version 3 removed with actionable
     * migration messages instead of the generic unknown-field failure.
     *
     * @param root parsed manifest root.
     * @throws FeatureManifestException if a retired section or identity field is present.
     */
    private void rejectRetiredFields(Map<String, Object> root) {
        if (root.containsKey("include")) {
            throw new FeatureManifestException("manifest root.include was removed in manifestVersion 4: a functional member is one 'features' entry keyed by "
                    + "its Artemis module id, technical members move to 'technical'.");
        }
        if (root.containsKey("provisional")) {
            throw new FeatureManifestException("manifest root.provisional was removed in manifestVersion 5: the 'features' entry keyed by the Artemis module "
                    + "id declares the membership itself, so delete the provisional entries and key every features entry by its module id.");
        }
        if (root.containsKey("renames")) {
            throw new FeatureManifestException("manifest root.renames was removed in manifestVersion 5: rename workflow references directly in the authored "
                    + "guided workflow.");
        }
        if (root.containsKey("exclude")) {
            throw new FeatureManifestException("manifest root.exclude was removed in manifestVersion 4: rename the section to 'notModeled'; its entries keep "
                    + "the anchor, reason, and rationale fields.");
        }
        if (root.containsKey("artemisCommitSha")) {
            throw new FeatureManifestException("manifest root.artemisCommitSha was removed in manifestVersion 3: the source revision is derived from the "
                    + "verified Artemis checkout, so the manifest no longer pins a commit. Delete the field.");
        }
        if (root.containsKey("artemisImageDigest")) {
            throw new FeatureManifestException("manifest root.artemisImageDigest was removed in manifestVersion 3: the runtime image reference is delivery "
                    + "configuration in delivery/artemis-runtime-image.json, not curation content. Delete the field.");
        }
    }

    /**
     * Parses the features section. Each entry is keyed by the Artemis module id, which implies its anchor.
     *
     * @param value raw YAML value of the features section, or null when absent.
     * @return parsed feature entries in manifest order.
     * @throws FeatureManifestException if an entry is malformed or carries a retired field.
     */
    private List<FeatureEntry> parseFeatures(Object value) {
        List<FeatureEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "features")) {
            String location = "features[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectRetiredEntryFields(entry, location);
            if (entry.containsKey("anchor")) {
                throw new FeatureManifestException(location + ".anchor was removed in manifestVersion 5: the anchor of a features entry is implied as "
                        + "module:<id>, so key the entry by the Artemis module id and delete the field.");
            }
            rejectUnknownFields(entry, FEATURE_FIELDS, location);
            entries.add(parseFeatureEntry(entry, location));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Parses the technical section, whose entries carry an anchor, the semantics of a features entry, and optionally
     * the Spring profile tokens the member activates.
     *
     * @param value raw YAML value of the technical section, or null when absent.
     * @return parsed technical entries in manifest order.
     * @throws FeatureManifestException if an entry is malformed or carries a retired field.
     */
    private List<TechnicalEntry> parseTechnical(Object value) {
        List<TechnicalEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "technical")) {
            String location = "technical[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectRetiredEntryFields(entry, location);
            for (String implied : List.of("kind", "category")) {
                if (entry.containsKey(implied)) {
                    throw new FeatureManifestException(location + "." + implied + " was removed in manifestVersion 5: kind 'feature' and category "
                            + "'technical' are implied by the technical section. Delete the field.");
                }
            }
            rejectUnknownFields(entry, TECHNICAL_FIELDS, location);
            entries.add(new TechnicalEntry(requiredString(entry, "anchor", location), parseFeatureEntry(entry, location), stringList(entry, "profiles", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Rejects the fields manifest version 5 derives with migration messages naming the replacement.
     *
     * @param entry parsed features or technical entry.
     * @param location location label of the entry.
     * @throws FeatureManifestException if a retired field is present.
     */
    private void rejectRetiredEntryFields(Map<String, Object> entry, String location) {
        if (entry.containsKey("artifactMappings")) {
            throw new FeatureManifestException(location + ".artifactMappings was removed in manifestVersion 5: functional environment mappings are derived from "
                    + "guarded Artemis structure and technical compose, profile, and environment mappings from the anchor, the 'profiles' list, and the "
                    + "@Profile-guarded injection sites; declare only confirmations and exceptions under 'configuration'.");
        }
        if (entry.containsKey("requiresCapabilities")) {
            throw new FeatureManifestException(location + ".requiresCapabilities was removed in manifestVersion 5: required capabilities are derived from the "
                    + "member's deployment inputs as <id>-service and <id>-secret. Delete the field.");
        }
        if (entry.containsKey("providesCapabilities")) {
            throw new FeatureManifestException(location + ".providesCapabilities was removed in manifestVersion 5: the bundled deployment profile provides every "
                    + "capability the active model requires, so nothing declares provided capabilities. Delete the field.");
        }
    }

    /**
     * Parses the id and modeling semantics shared by features and technical entries.
     *
     * @param entry parsed entry mapping.
     * @param location location label of the entry.
     * @return parsed feature semantics.
     * @throws FeatureManifestException if a field is malformed.
     */
    private FeatureEntry parseFeatureEntry(Map<String, Object> entry, String location) {
        return new FeatureEntry(requiredString(entry, "id", location), optionalString(entry, "group", location), optionalString(entry, "parent", location),
                optionality(entry, location), enumeratedString(entry, "category", CATEGORY_VALUES, location),
                enumeratedString(entry, "defaultState", DEFAULT_STATE_VALUES, location), optionalOrder(entry, location),
                parseConfigurationEntries(entry.get("configuration"), location), optionalString(entry, "name", location),
                optionalString(entry, "description", location), optionalString(entry, "documentationUrl", location), optionalString(entry, "rationale", location));
    }

    /**
     * Parses the configuration-key confirmations and exceptions of a features or technical entry. Keys must be unique within the
     * entry, the action must be a known one, and an exclude entry may not carry a secret flag, because a rejected key
     * is never emitted.
     *
     * @param value raw YAML value of the configuration field, or null when absent.
     * @param entryLocation location label of the owning entry.
     * @return parsed configuration entries in declaration order.
     * @throws FeatureManifestException if an entry is malformed.
     */
    private List<FeatureScopeManifest.ConfigurationEntry> parseConfigurationEntries(Object value, String entryLocation) {
        List<FeatureScopeManifest.ConfigurationEntry> entries = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        int index = 0;
        for (Object item : asList(value, entryLocation + ".configuration")) {
            String location = entryLocation + ".configuration[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, CONFIGURATION_FIELDS, location);
            String key = requiredString(entry, "key", location);
            requireUnique(keys, key, location + " repeats key '" + key + "'.");
            String action = enumeratedString(entry, "action", CONFIGURATION_ACTION_VALUES, location);
            Boolean secret = optionalBoolean(entry, "secret", location);
            if (FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE.equals(action) && secret != null) {
                throw new FeatureManifestException(location + " declares action 'exclude' and a secret flag; a rejected key is never emitted.");
            }
            entries.add(new FeatureScopeManifest.ConfigurationEntry(key, secret, action));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Parses the notModeled section.
     *
     * @param value raw YAML value of the notModeled section, or null when absent.
     * @return parsed exclusion entries in manifest order.
     * @throws FeatureManifestException if an entry is malformed.
     */
    private List<NotModeledEntry> parseNotModeled(Object value) {
        List<NotModeledEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : asList(value, "notModeled")) {
            String location = "notModeled[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, NOT_MODELED_FIELDS, location);
            entries.add(new NotModeledEntry(requiredString(entry, "anchor", location), optionalString(entry, "reason", location),
                    optionalString(entry, "rationale", location)));
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
     * Parses the ignored relation section.
     *
     * @param value raw YAML value of the ignoredRelations section, or null when absent.
     * @return parsed ignored relation entries in declaration order.
     * @throws FeatureManifestException if an entry is malformed or repeats a relation id.
     */
    private List<IgnoredRelationEntry> parseIgnoredRelations(Object value) {
        List<IgnoredRelationEntry> entries = new ArrayList<>();
        Set<String> relationIds = new LinkedHashSet<>();
        int index = 0;
        for (Object item : asList(value, "ignoredRelations")) {
            String location = "ignoredRelations[" + index + "]";
            Map<String, Object> entry = asMap(item, location);
            rejectUnknownFields(entry, IGNORED_RELATION_FIELDS, location);
            String id = requiredString(entry, "id", location);
            requireUnique(relationIds, id, "Duplicate ignored relation id '" + id + "'.");
            entries.add(new IgnoredRelationEntry(id, requiredString(entry, "rationale", location)));
            index++;
        }
        return List.copyOf(entries);
    }

    /**
     * Rejects duplicate anchors across the implied features anchors, the technical, and the notModeled sections, and
     * duplicate ids across the features, technical, and conceptualNodes sections. When no root is declared, the
     * implicit root id is reserved as well.
     *
     * @param features parsed feature entries.
     * @param technical parsed technical entries.
     * @param notModeled parsed exclusion entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @return every id the features, technical, and conceptualNodes sections declare plus the implicit root when no
     *         root is declared, in declaration order.
     * @throws FeatureManifestException if a duplicate is found.
     */
    private Set<String> validateUniqueness(List<FeatureEntry> features, List<TechnicalEntry> technical, List<NotModeledEntry> notModeled,
            List<ConceptualNode> conceptualNodes) {
        Set<String> anchors = new LinkedHashSet<>();
        for (FeatureEntry entry : features) {
            String anchor = FeatureScopeManifest.moduleAnchor(entry.id());
            requireUnique(anchors, anchor, "Duplicate manifest anchor '" + anchor + "'.");
        }
        for (TechnicalEntry entry : technical) {
            requireUnique(anchors, entry.anchor(), "Duplicate manifest anchor '" + entry.anchor() + "'.");
        }
        for (NotModeledEntry entry : notModeled) {
            requireUnique(anchors, entry.anchor(), "Duplicate manifest anchor '" + entry.anchor() + "'.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (FeatureEntry entry : features) {
            requireUnique(ids, entry.id(), "Duplicate manifest id '" + entry.id() + "' across features, technical, and conceptualNodes.");
        }
        for (TechnicalEntry entry : technical) {
            requireUnique(ids, entry.feature().id(), "Duplicate manifest id '" + entry.feature().id() + "' across features, technical, and conceptualNodes.");
        }
        for (ConceptualNode node : conceptualNodes) {
            requireUnique(ids, node.id(), "Duplicate manifest id '" + node.id() + "' across features, technical, and conceptualNodes.");
        }
        if (conceptualNodes.stream().noneMatch(node -> FeatureScopeManifest.KIND_ROOT.equals(node.kind()))) {
            requireUnique(ids, FeatureScopeManifest.IMPLICIT_ROOT_ID, "Manifest id '" + FeatureScopeManifest.IMPLICIT_ROOT_ID
                    + "' collides with the implicit root; declare a conceptual node of kind 'root' or rename the entry.");
        }
        return ids;
    }

    /**
     * Rejects parent and group references that do not point at another manifest-declared id or the implicit root. This
     * is a static check: the referenced id universe is fully known from the manifest alone.
     *
     * @param features parsed feature entries.
     * @param technical parsed technical entries.
     * @param conceptualNodes parsed conceptual nodes.
     * @param knownIds every id the manifest declares.
     * @throws FeatureManifestException if a reference points at an undeclared id.
     */
    private void validateInternalReferences(List<FeatureEntry> features, List<TechnicalEntry> technical, List<ConceptualNode> conceptualNodes,
            Set<String> knownIds) {
        for (ConceptualNode node : conceptualNodes) {
            requireKnownReference(knownIds, node.id(), node.parent());
        }
        for (FeatureEntry entry : features) {
            requireKnownReference(knownIds, entry.id(), entry.parent());
            requireKnownReference(knownIds, entry.id(), entry.group());
        }
        for (TechnicalEntry entry : technical) {
            requireKnownReference(knownIds, entry.feature().id(), entry.feature().parent());
            requireKnownReference(knownIds, entry.feature().id(), entry.feature().group());
        }
    }

    /**
     * Rejects constraint endpoints that do not point at a manifest-declared id.
     *
     * @param constraints parsed constraint entries.
     * @param knownIds every id the manifest declares.
     * @throws FeatureManifestException if a constraint references an undeclared feature id.
     */
    private void validateConstraintReferences(List<ConstraintEntry> constraints, Set<String> knownIds) {
        Set<String> constraintIds = new LinkedHashSet<>();
        for (ConstraintEntry constraint : constraints) {
            requireUnique(constraintIds, constraint.id(), "Duplicate constraint id '" + constraint.id() + "'.");
            requireKnownReference(knownIds, constraint.id(), constraint.source());
            requireKnownReference(knownIds, constraint.id(), constraint.target());
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
