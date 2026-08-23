package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import de.tum.cit.aet.artemis.featuremodel.shared.util.ClasspathJsonReader;
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

    static final String CATALOG_RESOURCE = "classpath:deployment-bindings/artemis-ansible-binding-catalog.json";

    private static final Set<String> KNOWN_EMISSIONS = Set.of(AnsibleBindingCatalog.EMISSION_ALWAYS, AnsibleBindingCatalog.EMISSION_NULL_OVERRIDE);

    private static final Set<String> KNOWN_UNSUPPORTED_DIRECTIONS = Set.of(AnsibleBindingCatalog.UNSUPPORTED_WHEN_SELECTED,
            AnsibleBindingCatalog.UNSUPPORTED_WHEN_DESELECTED);

    private static final List<String> SECTION_LABELS = List.of("technical database", "technical ciProvider", "feature");

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
        this.catalog = ClasspathJsonReader.read(resourceLoader, objectMapper, catalogLocation, AnsibleBindingCatalog.class, "the Ansible binding catalog");
        validate(this.catalog);
        log.info("Loaded Ansible binding catalog v{} for collection pin {} with {} feature bindings.", catalog.catalogVersion(), catalog.collectionPin(),
                catalog.features().size());
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
     * Validates the shipped catalog: identity fields, known emission, binding, and direction kinds, known environment
     * inputs, mandatory reasons, the technical axes, and unique group files.
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
        for (AnsibleBindingCatalog.BaselineEntry entry : catalog.baseline()) {
            if (!KNOWN_EMISSIONS.contains(entry.emission())) {
                throw invalid("Baseline entry '" + entry.var() + "' declares unknown emission kind '" + entry.emission() + "'.");
            }
            if (AnsibleBindingCatalog.EMISSION_NULL_OVERRIDE.equals(entry.emission()) && !StringUtils.hasText(entry.reason())) {
                throw invalid("Null-override entry '" + entry.var() + "' must record the collection default it defeats as its reason.");
            }
            if (entry.lines().isEmpty()) {
                throw invalid("Baseline entry '" + entry.var() + "' declares no rendered lines.");
            }
        }
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (!StringUtils.hasText(entry.input()) || entry.lines().isEmpty()) {
                throw invalid("Environment entry '" + entry.var() + "' must declare its input and rendered lines.");
            }
            if (!AnsibleBindingCatalog.FILE_COMMON_CONFIG.equals(entry.file()) && !AnsibleBindingCatalog.FILE_TARGET_MAIN.equals(entry.file())) {
                throw invalid("Environment entry '" + entry.var() + "' declares unknown target file '" + entry.file() + "'.");
            }
            if (RemoteEnvironmentValues.Input.byName(entry.input()) == null) {
                throw invalid("Environment entry '" + entry.var() + "' declares unknown input '" + entry.input() + "'.");
            }
        }
        for (AnsibleBindingCatalog.SecretEntry entry : catalog.secrets()) {
            if (!StringUtils.hasText(entry.vaultPath()) || !StringUtils.hasText(entry.vaultField())) {
                throw invalid("Secret entry '" + entry.var() + "' must declare its vault path and field.");
            }
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
        Set<String> groupVarsFiles = new HashSet<>();
        Set<String> memberships = new HashSet<>();
        for (Map<String, AnsibleBindingCatalog.FeatureBinding> section : catalog.sections()) {
            for (Map.Entry<String, AnsibleBindingCatalog.FeatureBinding> entry : section.entrySet()) {
                AnsibleBindingCatalog.FeatureBinding binding = entry.getValue();
                if (!AnsibleBindingCatalog.BINDING_BOUND.equals(binding.binding())) {
                    continue;
                }
                if (!groupVarsFiles.add(binding.groupVarsFile())) {
                    throw invalid("Group values file '" + binding.groupVarsFile() + "' is declared by more than one bound binding ('" + entry.getKey() + "').");
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
     * Validates a bound binding: it must name its membership group and group values file and render content.
     *
     * @param section section label for error messages.
     * @param featureId bound feature id.
     * @param binding bound binding.
     * @throws FeatureModelLoadException if the binding is inconsistent.
     */
    private void validateBoundBinding(String section, String featureId, AnsibleBindingCatalog.FeatureBinding binding) {
        if (!StringUtils.hasText(binding.membership()) || !StringUtils.hasText(binding.groupVarsFile())) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' must declare its membership group and group values file.");
        }
        if (binding.lines().isEmpty()) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' declares no rendered lines.");
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
