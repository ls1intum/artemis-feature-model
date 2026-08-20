package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

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

    static final String CATALOG_RESOURCE = "classpath:deployment-bindings/artemis-ansible-binding-catalog.json";

    private static final Set<String> KNOWN_EMISSIONS = Set.of(AnsibleBindingCatalog.EMISSION_ALWAYS, AnsibleBindingCatalog.EMISSION_NULL_OVERRIDE);

    private static final Set<String> KNOWN_BINDINGS = Set.of(AnsibleBindingCatalog.BINDING_BOUND, AnsibleBindingCatalog.BINDING_NO_OP,
            AnsibleBindingCatalog.BINDING_UNSUPPORTED);

    private final AnsibleBindingCatalog catalog;

    /**
     * Creates the loader against the bundled classpath catalog.
     *
     * @param resourceLoader Spring resource loader used to resolve the catalog resource.
     * @param objectMapper Jackson mapper used to parse the catalog.
     * @throws FeatureModelLoadException if the catalog cannot be read, parsed, or validated.
     */
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
        this.catalog = loadCatalog(resourceLoader, objectMapper, catalogLocation);
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
     * Loads and parses the catalog resource.
     *
     * @param resourceLoader Spring resource loader.
     * @param objectMapper Jackson mapper.
     * @param catalogLocation catalog resource location.
     * @return parsed catalog.
     * @throws FeatureModelLoadException if the resource cannot be read or parsed.
     */
    private AnsibleBindingCatalog loadCatalog(ResourceLoader resourceLoader, ObjectMapper objectMapper, String catalogLocation) {
        Resource resource = resourceLoader.getResource(catalogLocation);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, AnsibleBindingCatalog.class);
        }
        catch (IOException | RuntimeException e) {
            log.error("Could not load the Ansible binding catalog from {}.", catalogLocation, e);
            throw new FeatureModelLoadException("Could not load the Ansible binding catalog from " + catalogLocation + ".", e);
        }
    }

    /**
     * Validates the shipped catalog: identity fields, known emission and binding kinds, mandatory reasons, and
     * complete value-gated blocks.
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
            if (AnsibleBindingCatalog.EMISSION_NULL_OVERRIDE.equals(entry.emission()) && isBlank(entry.reason())) {
                throw invalid("Null-override entry '" + entry.var() + "' must record the collection default it defeats as its reason.");
            }
            if (entry.lines().isEmpty()) {
                throw invalid("Baseline entry '" + entry.var() + "' declares no rendered lines.");
            }
        }
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (isBlank(entry.input()) || entry.lines().isEmpty()) {
                throw invalid("Environment entry '" + entry.var() + "' must declare its input and rendered lines.");
            }
            if (!AnsibleBindingCatalog.FILE_COMMON_CONFIG.equals(entry.file()) && !AnsibleBindingCatalog.FILE_TARGET_MAIN.equals(entry.file())) {
                throw invalid("Environment entry '" + entry.var() + "' declares unknown target file '" + entry.file() + "'.");
            }
        }
        for (AnsibleBindingCatalog.SecretEntry entry : catalog.secrets()) {
            if (isBlank(entry.vaultPath()) || isBlank(entry.vaultField())) {
                throw invalid("Secret entry '" + entry.var() + "' must declare its vault path and field.");
            }
        }
        validateBindings("technical database", catalog.technical() == null ? Map.of() : catalog.technical().database());
        validateBindings("technical ciProvider", catalog.technical() == null ? Map.of() : catalog.technical().ciProvider());
        validateBindings("feature", catalog.features());
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
            if (!KNOWN_BINDINGS.contains(binding.binding())) {
                throw invalid("The " + section + " binding of '" + featureId + "' declares unknown classification '" + binding.binding() + "'.");
            }
            switch (binding.binding()) {
                case AnsibleBindingCatalog.BINDING_BOUND -> validateBoundBinding(section, featureId, binding);
                case AnsibleBindingCatalog.BINDING_NO_OP -> {
                    if (isBlank(binding.reason())) {
                        throw invalid("The no-op " + section + " binding of '" + featureId + "' must record its reason.");
                    }
                }
                case AnsibleBindingCatalog.BINDING_UNSUPPORTED -> {
                    if (isBlank(binding.missingVariable()) && isBlank(binding.reason())) {
                        throw invalid("The unsupported " + section + " binding of '" + featureId + "' must record its missing variable or reason.");
                    }
                }
                default -> throw invalid("The " + section + " binding of '" + featureId + "' declares unknown classification '" + binding.binding() + "'.");
            }
        }
    }

    /**
     * Validates a bound binding: rendered content must exist and every gated field of a value-gated block must be
     * complete, so the shipped catalog can never emit a partial value block.
     *
     * @param section section label for error messages.
     * @param featureId bound feature id.
     * @param binding bound binding.
     * @throws FeatureModelLoadException if the binding is inconsistent.
     */
    private void validateBoundBinding(String section, String featureId, AnsibleBindingCatalog.FeatureBinding binding) {
        if (isBlank(binding.membership()) || isBlank(binding.groupVarsFile())) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' must declare its membership group and group values file.");
        }
        boolean valueGated = AnsibleBindingCatalog.GATING_VALUE_GATED.equals(binding.gating());
        if (valueGated) {
            List<AnsibleBindingCatalog.GatedField> gatedFields = binding.gatedFields();
            if (gatedFields.isEmpty()) {
                throw invalid("The value-gated binding of '" + featureId + "' must declare its gated fields.");
            }
            for (AnsibleBindingCatalog.GatedField field : gatedFields) {
                if (isBlank(field.name()) || isBlank(field.line())) {
                    throw invalid("The value-gated binding of '" + featureId + "' has an incomplete gated field; the shipped catalog must be complete.");
                }
            }
        }
        else if (binding.lines().isEmpty()) {
            throw invalid("The bound " + section + " binding of '" + featureId + "' declares no rendered lines.");
        }
    }

    /**
     * Checks whether a string is null or blank.
     *
     * @param value string to check.
     * @return true for null or blank.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
