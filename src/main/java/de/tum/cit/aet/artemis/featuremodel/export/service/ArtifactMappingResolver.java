package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.EnvironmentVariableNames;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolutionResult;
import tools.jackson.databind.JsonNode;

/**
 * Resolves a feature selection into overlay entries, structured environment requirements, and report fragments.
 *
 * <p>
 * Only mappings whose {@code target} is the generated overlay file produce overlay entries; mappings for other targets
 * (for example {@code .env} contributions declared by generated models) are ignored here and belong to their own
 * writers. Selection mappings are written for every feature that has one: the selected value when the feature is
 * selected, the deselected value otherwise. Environment mappings are written only when the owning feature is selected;
 * each writes exactly one {@code ${VARIABLE}} placeholder with a deterministically derived name and produces one
 * structured environment requirement. The resolver never invents an external-service value and never writes a
 * plaintext secret into the overlay. LTI registration and selected features without any mapping are reported as
 * warnings or informational notes.
 */
@Component
public class ArtifactMappingResolver {

    /** Mapping target of the generated Spring configuration overlay; the only target this resolver writes. */
    static final String OVERLAY_TARGET = "application-feature-model.yml";

    private static final String LTI_FEATURE_ID = "lti";

    private static final String LTI_TOGGLE_PATH = "artemis.lti.enabled";

    private final Map<String, String> catalogTypesByKey;

    /**
     * Creates the resolver against the catalog of the validated runtime bundle, so every catalog-keyed environment
     * requirement carries the catalog type its demo default and validation use.
     *
     * @param runtimeBundle validated process-stable runtime bundle.
     */
    @Autowired
    public ArtifactMappingResolver(RuntimeFeatureModelBundle runtimeBundle) {
        this(runtimeBundle.catalog());
    }

    /**
     * Creates the resolver against an explicit catalog. Convenient for focused unit tests.
     *
     * @param catalog Artemis config-key catalog used to type catalog-keyed requirements.
     */
    public ArtifactMappingResolver(ArtemisConfigKeyCatalog catalog) {
        Map<String, String> typesByKey = new LinkedHashMap<>();
        for (ArtemisConfigKeyCatalog.CatalogKey key : catalog.keys()) {
            typesByKey.putIfAbsent(key.key(), key.type());
        }
        this.catalogTypesByKey = Map.copyOf(typesByKey);
    }

    /**
     * Resolves overlay entries, environment requirements, and report fragments for a selection.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @return resolution result with overlay entries, environment requirements, and messages.
     */
    public ResolutionResult resolve(FeatureModel model, Set<String> selectedFeatureIds) {
        List<OverlayEntry> entries = new ArrayList<>();
        List<EnvironmentRequirement> requirements = new ArrayList<>();
        List<GenerationMessage> messages = new ArrayList<>();
        List<String> selectedWithoutMapping = new ArrayList<>();

        for (FeatureNode feature : model.features()) {
            boolean selected = selectedFeatureIds.contains(feature.id());
            if (selected && feature.selectable() && feature.artifactMappings().isEmpty()) {
                selectedWithoutMapping.add(feature.name());
            }
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (!OVERLAY_TARGET.equals(mapping.target())) {
                    continue;
                }
                if (mapping.isSelection()) {
                    addSelectionEntry(mapping, selected, entries);
                }
                else if (mapping.isEnvironment() && selected) {
                    addEnvironmentEntry(feature, mapping, entries, requirements, messages);
                }
            }
        }

        addLtiRegistrationWarning(selectedFeatureIds, messages);
        addNoMappingNote(selectedWithoutMapping, messages);

        return new ResolutionResult(entries, requirements, messages);
    }

    /**
     * Adds the selection entry for a feature, writing the selected or deselected value.
     *
     * @param mapping selection mapping.
     * @param selected whether the owning feature is selected.
     * @param entries accumulating overlay entries.
     */
    private void addSelectionEntry(ArtifactMapping mapping, boolean selected, List<OverlayEntry> entries) {
        JsonNode node = selected ? mapping.valueWhenSelected() : mapping.valueWhenDeselected();
        if (node == null) {
            return;
        }
        entries.add(new OverlayEntry(mapping.path(), toJavaValue(node)));
    }

    /**
     * Writes the {@code ${VARIABLE}} placeholder of a selected environment mapping and records its structured
     * requirement.
     *
     * @param feature owning feature.
     * @param mapping environment mapping.
     * @param entries accumulating overlay entries.
     * @param requirements accumulating environment requirements.
     * @param messages accumulating warnings.
     */
    private void addEnvironmentEntry(FeatureNode feature, ArtifactMapping mapping, List<OverlayEntry> entries, List<EnvironmentRequirement> requirements,
            List<GenerationMessage> messages) {
        String name = EnvironmentVariableNames.derive(mapping.path());
        entries.add(new OverlayEntry(mapping.path(), "${" + name + "}"));
        requirements.add(new EnvironmentRequirement(name, feature.id(), feature.name(), mapping.path(), catalogTypesByKey.get(mapping.path()),
                mapping.secret(), EnvironmentRequirement.SOURCE_ARTIFACT_MAPPING, environmentPurpose(feature, mapping)));
        messages.add(GenerationMessage.warning(feature.id(), mapping.path(),
                "Value for '" + mapping.path() + "' is provided via environment variable " + name + "; set it before deployment."));
    }

    /**
     * Builds the human-readable purpose of an artifact-mapping environment requirement.
     *
     * @param feature owning feature.
     * @param mapping environment mapping.
     * @return requirement purpose.
     */
    private String environmentPurpose(FeatureNode feature, ArtifactMapping mapping) {
        String valueKind = mapping.secret() ? "Secret value" : "Value";
        return valueKind + " for configuration key '" + mapping.path() + "' required by " + feature.name() + ".";
    }

    /**
     * Adds the LTI manual-registration warning when LTI is selected.
     *
     * @param selectedFeatureIds selected feature ids.
     * @param messages accumulating warnings.
     */
    private void addLtiRegistrationWarning(Set<String> selectedFeatureIds, List<GenerationMessage> messages) {
        if (selectedFeatureIds.contains(LTI_FEATURE_ID)) {
            messages.add(GenerationMessage.warning(LTI_FEATURE_ID, LTI_TOGGLE_PATH, "LTI is enabled, but LTI platform registration is managed in the "
                    + "Artemis database by an administrator and cannot be fully configured by the generated overlay."));
        }
    }

    /**
     * Adds one informational note listing selected features that produce no overlay entry.
     *
     * @param selectedWithoutMapping display names of selected features without any mapping.
     * @param messages accumulating notes.
     */
    private void addNoMappingNote(List<String> selectedWithoutMapping, List<GenerationMessage> messages) {
        if (selectedWithoutMapping.isEmpty()) {
            return;
        }
        messages.add(GenerationMessage.info(null,
                "These selected features are managed by Artemis defaults and have no generated configuration entry: " + String.join(", ", selectedWithoutMapping) + "."));
    }

    /**
     * Converts a JSON selection value to a typed Java value for the overlay writer.
     *
     * @param node JSON value node.
     * @return Boolean, Long, Double, Number, or String value.
     */
    private Object toJavaValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asString();
    }
}
