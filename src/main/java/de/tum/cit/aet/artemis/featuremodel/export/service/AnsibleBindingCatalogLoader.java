package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads and validates the curated Ansible binding catalog from the application classpath. The catalog is read from
 * the same location in both runtime source modes; it is application-owned deployment knowledge, never part of a model
 * snapshot. Loading is eager and fail-closed: a missing, malformed, or internally inconsistent catalog fails at
 * startup instead of at the first remote-ansible generation.
 */
@Component
public class AnsibleBindingCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(AnsibleBindingCatalogLoader.class);

    static final String CATALOG_RESOURCE = "classpath:deployment-bindings/artemis-ansible-binding-catalog.yml";

    private static final Set<String> KNOWN_UNSUPPORTED_DIRECTIONS = Set.of(AnsibleBindingCatalog.UNSUPPORTED_WHEN_SELECTED,
            AnsibleBindingCatalog.UNSUPPORTED_WHEN_DESELECTED);

    /** Shape of a user-provisioned environment-variable name. */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private static final List<String> SECTION_LABELS = List.of("technical database", "technical ciProvider", "feature");

    private static final Pattern ENV_LOOKUP = Pattern.compile("lookup\\(\\s*'ansible\\.builtin\\.env'\\s*,\\s*'([^']*)'\\s*\\)");

    private final AnsibleBindingCatalog catalog;

    /**
     * Creates the loader against the bundled classpath catalog.
     *
     * @param resourceLoader Spring resource loader used to resolve the catalog resource.
     * @param objectMapper Jackson mapper used to parse the catalog.
     * @throws FeatureModelLoadException if the catalog cannot be read, parsed, or validated.
     */
    @Autowired
    public AnsibleBindingCatalogLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(resourceLoader, objectMapper, CATALOG_RESOURCE);
    }

    /**
     * Creates the loader against a caller-provided catalog location; used by tests to validate broken catalogs.
     *
     * @param resourceLoader Spring resource loader used to resolve the catalog resource.
     * @param objectMapper Jackson mapper used to parse the catalog.
     * @param catalogLocation catalog resource location.
     * @throws FeatureModelLoadException if the catalog cannot be read, parsed, or validated.
     */
    AnsibleBindingCatalogLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper, String catalogLocation) {
        try (InputStream input = resourceLoader.getResource(catalogLocation).getInputStream()) {
            Map<String, Object> raw = new Yaml().load(input);
            prepareCatalog(raw);
            this.catalog = objectMapper.convertValue(raw, AnsibleBindingCatalog.class);
        }
        catch (IOException | YAMLException | IllegalArgumentException e) {
            throw new FeatureModelLoadException("Could not load the Ansible binding catalog: " + e.getMessage(), e);
        }
        validate(this.catalog);
        log.info("Loaded Ansible binding catalog v{} for collection pin {} with {} feature bindings.", catalog.catalogVersion(), catalog.collectionPin(),
                catalog.features().size());
    }

    /**
     * Validates authored keys before adding derived fields to the binding input.
     * @param raw parsed YAML catalog.
     * @throws FeatureModelLoadException if a key or required block is invalid.
     */
    @SuppressWarnings("unchecked")
    private void prepareCatalog(Map<String, Object> raw) {
        requireKeys(raw, "catalog", Set.of("catalogVersion", "collectionPin", "curationSource", "files", "technical", "features"));
        Map<String, Object> files = (Map<String, Object>) raw.get("files");
        requireKeys(files, "files", Set.of("targetMain", "targetSecrets", "commonConfig"));
        for (String name : List.of("targetMain", "targetSecrets", "commonConfig")) {
            Map<String, Object> block = (Map<String, Object>) files.get(name);
            requireKeys(block, name, Set.of("content"));
            prepareContent(block, name);
        }
        Map<String, Object> technical = (Map<String, Object>) raw.get("technical");
        requireKeys(technical, "technical database and ciProvider", Set.of("database", "ciProvider"));
        prepareBindings((Map<String, Object>) technical.get("database"), "database");
        prepareBindings((Map<String, Object>) technical.get("ciProvider"), "ciProvider");
        raw.putIfAbsent("features", Map.of());
        prepareBindings((Map<String, Object>) raw.get("features"), "features");
    }

    /**
     * Prepares content and references for each binding without changing classification.
     * @param bindings authored binding map.
     * @param section section label.
     * @throws FeatureModelLoadException if authored content is invalid.
     */
    @SuppressWarnings("unchecked")
    private void prepareBindings(Map<String, Object> bindings, String section) {
        if (bindings == null) {
            throw invalid("The catalog must declare technical database and ciProvider bindings.");
        }
        for (var entry : bindings.entrySet()) {
            Map<String, Object> binding = (Map<String, Object>) entry.getValue();
            String label = section + "." + entry.getKey();
            requireKeys(binding, label, Set.of("binding", "gating", "membership", "content", "unsupportedWhen", "missingVariable", "reason", "evidence"));
            if (binding.containsKey("content")) {
                prepareContent(binding, label);
            }
            else {
                binding.put("envReferences", List.of());
            }
        }
    }

    /**
     * Rejects missing structures and misspelled authored keys.
     * @param values authored structure.
     * @param label structure label.
     * @param allowed accepted keys.
     * @throws FeatureModelLoadException if the structure is missing or has unknown keys.
     */
    private void requireKeys(Map<String, Object> values, String label, Set<String> allowed) {
        if (values == null) {
            throw invalid("Missing " + label + " block.");
        }
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw invalid("Unknown key '" + key + "' in " + label + ".");
            }
        }
    }

    /**
     * Strips catalog annotations and parses the emitted YAML to derive scalar consumers.
     * @param block authored content block.
     * @param label block name for diagnostics.
     * @throws FeatureModelLoadException if content is absent or malformed.
     */
    private void prepareContent(Map<String, Object> block, String label) {
        String authored = (String) block.get("content");
        if (authored == null) {
            throw invalid("Missing content in " + label + ".");
        }
        String content = String.join("\n", Arrays.stream(authored.split("\n", -1)).filter(line -> !line.startsWith("#:")).toList());
        if (!content.equals("---") && !content.startsWith("---\n")) {
            throw invalid("Content in " + label + " must start with ---. ");
        }
        List<AnsibleBindingCatalog.EnvReference> references = new ArrayList<>();
        try {
            collectReferences(new Yaml().load(content), "", references);
        }
        catch (YAMLException e) {
            throw invalid("Invalid YAML content in " + label + ": " + e.getMessage());
        }
        block.put("content", content);
        block.put("envReferences", references);
    }

    /**
     * Walks parsed values in document order and records lookup consumers.
     * @param value parsed YAML value.
     * @param path dotted consumer path.
     * @param references accumulating references.
     * @throws FeatureModelLoadException if a lookup name is not uppercase.
     */
    private void collectReferences(Object value, String path, List<AnsibleBindingCatalog.EnvReference> references) {
        if (value instanceof Map<?, ?> mapping) {
            for (var entry : mapping.entrySet()) {
                String child = path.isEmpty() ? entry.getKey().toString() : path + "." + entry.getKey();
                collectReferences(entry.getValue(), child, references);
            }
        }
        else if (value instanceof List<?> sequence) {
            for (Object item : sequence) {
                collectReferences(item, path, references);
            }
        }
        else if (value instanceof String scalar) {
            var matcher = ENV_LOOKUP.matcher(scalar);
            while (matcher.find()) {
                String name = matcher.group(1);
                validateEnvVarName("Content", path, name);
                references.add(new AnsibleBindingCatalog.EnvReference(name, path));
            }
        }
    }

    /**
     * Returns the loaded, validated catalog.
     *
     * @return Ansible binding catalog.
     */
    public AnsibleBindingCatalog catalog() {
        return catalog;
    }

    /**
     * Validates the shipped catalog: identity fields, known emission, binding, and direction kinds, declared
     * environment-variable names and their rendered lookup expressions, mandatory reasons, the technical axes, and
     * unique group files.
     *
     * @param catalog parsed catalog.
     * @throws FeatureModelLoadException if the catalog is internally inconsistent.
     */
    private void validate(AnsibleBindingCatalog catalog) {
        if (catalog.catalogVersion() <= 0) {
            throw invalid("The catalog must declare a positive catalogVersion.");
        }
        if (catalog.collectionPin() == null || !catalog.collectionPin().matches("[0-9a-f]{40}")) {
            throw invalid("The catalog must pin the consulted collection commit as a full 40-character SHA.");
        }
        if (catalog.technical().database().isEmpty() || catalog.technical().ciProvider().isEmpty()) {
            throw invalid("The catalog must declare technical database and ciProvider bindings.");
        }
        List<Map<String, AnsibleBindingCatalog.FeatureBinding>> sections = catalog.sections();
        for (int index = 0; index < sections.size(); index++) {
            validateBindings(SECTION_LABELS.get(index), sections.get(index));
        }
        validateUniqueGroupFiles(catalog);
    }

    /**
     * Validates that every bound binding renders a distinct group values file and joins a distinct membership group,
     * so two bindings can never overwrite each other's file or wire the same group twice.
     *
     * @param catalog parsed catalog.
     * @throws FeatureModelLoadException if a group values file or membership group is declared twice.
     */
    private void validateUniqueGroupFiles(AnsibleBindingCatalog catalog) {
        Set<String> memberships = new HashSet<>();
        for (Map<String, AnsibleBindingCatalog.FeatureBinding> section : catalog.sections()) {
            for (Map.Entry<String, AnsibleBindingCatalog.FeatureBinding> entry : section.entrySet()) {
                AnsibleBindingCatalog.FeatureBinding binding = entry.getValue();
                if (!AnsibleBindingCatalog.BINDING_BOUND.equals(binding.binding())) {
                    continue;
                }
                if (!memberships.add(binding.membership())) {
                    throw invalid("Membership group '" + binding.membership() + "' is declared by more than one bound binding ('" + entry.getKey() + "').");
                }
            }
        }
    }

    /**
     * Validates one map of feature bindings.
     *
     * @param section section label for error messages.
     * @param bindings bindings by feature id.
     * @throws FeatureModelLoadException if a binding is inconsistent.
     */
    private void validateBindings(String section, Map<String, AnsibleBindingCatalog.FeatureBinding> bindings) {
        for (Map.Entry<String, AnsibleBindingCatalog.FeatureBinding> entry : bindings.entrySet()) {
            String featureId = entry.getKey();
            AnsibleBindingCatalog.FeatureBinding binding = entry.getValue();
            switch (binding.binding()) {
                case AnsibleBindingCatalog.BINDING_BOUND -> validateBoundBinding(section, featureId, binding);
                case AnsibleBindingCatalog.BINDING_NO_OP -> {
                    if (!StringUtils.hasText(binding.reason())) {
                        throw invalid("The no-op " + section + " binding of '" + featureId + "' must record its reason.");
                    }
                }
                case AnsibleBindingCatalog.BINDING_UNSUPPORTED -> {
                    if (!StringUtils.hasText(binding.missingVariable()) && !StringUtils.hasText(binding.reason())) {
                        throw invalid("The unsupported " + section + " binding of '" + featureId + "' must record its missing variable or reason.");
                    }
                    if (binding.unsupportedWhen() != null && !KNOWN_UNSUPPORTED_DIRECTIONS.contains(binding.unsupportedWhen())) {
                        throw invalid("The unsupported " + section + " binding of '" + featureId + "' declares unknown direction '" + binding.unsupportedWhen() + "'.");
                    }
                }
                case null, default -> throw invalid("The " + section + " binding of '" + featureId + "' declares unknown classification '" + binding.binding() + "'.");
            }
        }
    }

    /**
     * Validates a bound binding: it must name its membership group, render content, and carry
     * a known gating. Deselection gating is a feature-section semantics; the technical axes are selection-resolved
     * xor choices and must stay presence-gated.
     *
     * @param section section label for error messages.
     * @param featureId bound feature id.
     * @param binding bound binding.
     * @throws FeatureModelLoadException if the binding is inconsistent.
     */
    private void validateBoundBinding(String section, String featureId, AnsibleBindingCatalog.FeatureBinding binding) {
        if (!StringUtils.hasText(binding.membership())) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' must declare its membership group.");
        }
        if (!StringUtils.hasText(binding.content())) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' declares no rendered content.");
        }
        if (binding.gating() != null && !AnsibleBindingCatalog.GATING_DESELECTED.equals(binding.gating())) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' declares unknown gating '" + binding.gating() + "'.");
        }
        if (binding.gating() != null && section.startsWith("technical")) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' must not declare a gating; technical choices are selection-resolved.");
        }
    }

    /**
     * Validates a declared user-provisioned environment-variable name.
     *
     * @param owner owning entry label for error messages.
     * @param entryName entry or consumer name for error messages.
     * @param envVar declared environment-variable name.
     * @throws FeatureModelLoadException if the name is absent or not an uppercase environment-variable token.
     */
    private void validateEnvVarName(String owner, String entryName, String envVar) {
        if (!StringUtils.hasText(envVar) || !ENV_VAR_PATTERN.matcher(envVar).matches()) {
            throw invalid(owner + " '" + entryName + "' must declare an uppercase environment-variable name.");
        }
    }

    /**
     * Creates the fail-closed load exception for an inconsistent catalog.
     *
     * @param message validation failure description.
     * @return load exception.
     */
    private FeatureModelLoadException invalid(String message) {
        return new FeatureModelLoadException("Invalid Ansible binding catalog: " + message);
    }
}
