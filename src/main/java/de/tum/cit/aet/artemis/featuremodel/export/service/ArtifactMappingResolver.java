package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ConsumedParameter;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.OmittedMapping;
import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolutionResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolvedProfileValue;
import tools.jackson.databind.JsonNode;

/**
 * Resolves a feature selection and a deployment profile into overlay entries and report fragments.
 *
 * <p>
 * Toggle mappings are written for every feature that has one: the selected value when the feature is selected, the
 * deselected value otherwise. Profile-value mappings are written only when the owning feature is selected, copying the
 * value of the named profile parameter. Missing required parameters, placeholder values, unresolved Vault references,
 * plaintext secrets, LTI registration, deprecated profile keys, and selected features without any mapping are reported
 * as warnings or informational notes. The resolver never writes a plaintext secret into the overlay.
 */
@Component
public class ArtifactMappingResolver {

    private static final String LTI_FEATURE_ID = "lti";

    private static final String LTI_TOGGLE_PATH = "artemis.lti.enabled";

    private final ProfileParameterResolver profileParameterResolver;

    /**
     * Creates the artifact mapping resolver.
     *
     * @param profileParameterResolver resolver used to classify and type profile parameter values.
     */
    public ArtifactMappingResolver(ProfileParameterResolver profileParameterResolver) {
        this.profileParameterResolver = profileParameterResolver;
    }

    /**
     * Resolves overlay entries and report fragments for a selection against a profile.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @param profile active deployment profile.
     * @return resolution result with overlay entries, environment variables, consumed parameters, omitted mappings, and messages.
     */
    public ResolutionResult resolve(FeatureModel model, Set<String> selectedFeatureIds, DeploymentProfile profile) {
        List<OverlayEntry> entries = new ArrayList<>();
        Set<String> environmentVariables = new LinkedHashSet<>();
        List<ConsumedParameter> consumed = new ArrayList<>();
        List<OmittedMapping> omitted = new ArrayList<>();
        List<GenerationMessage> messages = new ArrayList<>();
        List<String> selectedWithoutMapping = new ArrayList<>();

        for (FeatureNode feature : model.features()) {
            boolean selected = selectedFeatureIds.contains(feature.id());
            if (selected && feature.selectable() && feature.artifactMappings().isEmpty()) {
                selectedWithoutMapping.add(feature.name());
            }
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (mapping.isToggle()) {
                    addToggleEntry(mapping, selected, entries);
                }
                else if (mapping.isProfileValue() && selected) {
                    resolveProfileMapping(feature, mapping, profile, entries, environmentVariables, consumed, omitted, messages);
                }
            }
        }

        addLtiRegistrationWarning(selectedFeatureIds, messages);
        addNoMappingNote(selectedWithoutMapping, messages);
        addDeprecatedAliasWarnings(profile, messages);

        return new ResolutionResult(entries, new ArrayList<>(environmentVariables), consumed, omitted, messages);
    }

    /**
     * Adds the toggle entry for a feature, writing the selected or deselected value.
     *
     * @param mapping toggle mapping.
     * @param selected whether the owning feature is selected.
     * @param entries accumulating overlay entries.
     */
    private void addToggleEntry(ArtifactMapping mapping, boolean selected, List<OverlayEntry> entries) {
        JsonNode node = selected ? mapping.valueWhenSelected() : mapping.valueWhenDeselected();
        if (node == null) {
            return;
        }
        entries.add(new OverlayEntry(mapping.path(), toJavaValue(node)));
    }

    /**
     * Resolves a profile-value mapping for a selected feature, writing the value or reporting why it was skipped.
     *
     * @param feature owning feature.
     * @param mapping profile-value mapping.
     * @param profile active deployment profile.
     * @param entries accumulating overlay entries.
     * @param environmentVariables accumulating environment variable names.
     * @param consumed accumulating consumed parameters.
     * @param omitted accumulating omitted mappings.
     * @param messages accumulating warnings and notes.
     */
    private void resolveProfileMapping(FeatureNode feature, ArtifactMapping mapping, DeploymentProfile profile, List<OverlayEntry> entries,
            Set<String> environmentVariables, List<ConsumedParameter> consumed, List<OmittedMapping> omitted, List<GenerationMessage> messages) {
        Map<String, String> parameters = profile.parameters();
        String profileKey = mapping.valueFromProfile();
        if (!parameters.containsKey(profileKey)) {
            handleMissingProfileValue(feature, mapping, omitted, messages);
            return;
        }
        ResolvedProfileValue resolved = profileParameterResolver.resolve(parameters.get(profileKey));
        switch (resolved.kind()) {
            case ENV -> addEnvironmentValue(feature, mapping, profileKey, resolved, entries, environmentVariables, consumed, messages);
            case VAULT -> addVaultOmission(feature, mapping, omitted, messages);
            case LITERAL -> addLiteralValue(feature, mapping, profileKey, resolved, entries, consumed, omitted, messages);
        }
    }

    /**
     * Writes an environment-reference value as a placeholder and records its variable.
     *
     * @param feature owning feature.
     * @param mapping profile-value mapping.
     * @param profileKey profile parameter key.
     * @param resolved resolved environment value.
     * @param entries accumulating overlay entries.
     * @param environmentVariables accumulating environment variable names.
     * @param consumed accumulating consumed parameters.
     * @param messages accumulating warnings.
     */
    private void addEnvironmentValue(FeatureNode feature, ArtifactMapping mapping, String profileKey, ResolvedProfileValue resolved,
            List<OverlayEntry> entries, Set<String> environmentVariables, List<ConsumedParameter> consumed, List<GenerationMessage> messages) {
        entries.add(new OverlayEntry(mapping.path(), resolved.yamlValue()));
        environmentVariables.add(resolved.envVarName());
        consumed.add(new ConsumedParameter(feature.id(), profileKey, mapping.path(), true, ConsumedParameter.SOURCE_ENV));
        messages.add(GenerationMessage.warning(feature.id(), mapping.path(),
                "Value for '" + mapping.path() + "' is provided via environment variable " + resolved.envVarName() + "; set it before deployment."));
    }

    /**
     * Reports an unresolved Vault reference and omits the value from the overlay.
     *
     * @param feature owning feature.
     * @param mapping profile-value mapping.
     * @param omitted accumulating omitted mappings.
     * @param messages accumulating warnings.
     */
    private void addVaultOmission(FeatureNode feature, ArtifactMapping mapping, List<OmittedMapping> omitted, List<GenerationMessage> messages) {
        omitted.add(new OmittedMapping(feature.id(), mapping.path(), "Vault reference is not resolved in demo mode."));
        messages.add(GenerationMessage.warning(feature.id(), mapping.path(),
                "Vault reference for '" + mapping.path() + "' is not resolved; provide the value through your secret manager before deployment."));
    }

    /**
     * Writes a literal value, refusing plaintext secrets and warning about placeholder values.
     *
     * @param feature owning feature.
     * @param mapping profile-value mapping.
     * @param profileKey profile parameter key.
     * @param resolved resolved literal value.
     * @param entries accumulating overlay entries.
     * @param consumed accumulating consumed parameters.
     * @param omitted accumulating omitted mappings.
     * @param messages accumulating warnings.
     */
    private void addLiteralValue(FeatureNode feature, ArtifactMapping mapping, String profileKey, ResolvedProfileValue resolved, List<OverlayEntry> entries,
            List<ConsumedParameter> consumed, List<OmittedMapping> omitted, List<GenerationMessage> messages) {
        if (mapping.secret()) {
            omitted.add(new OmittedMapping(feature.id(), mapping.path(), "Secret value must be an env: or vault: reference; plaintext is not written."));
            messages.add(GenerationMessage.warning(feature.id(), mapping.path(), "Secret '" + mapping.path()
                    + "' must be provided as an env: or vault: reference in the deployment profile; it was not written to the overlay."));
            return;
        }
        entries.add(new OverlayEntry(mapping.path(), resolved.yamlValue()));
        consumed.add(new ConsumedParameter(feature.id(), profileKey, mapping.path(), false, ConsumedParameter.SOURCE_LITERAL));
        if (resolved.placeholder()) {
            messages.add(GenerationMessage.warning(feature.id(), mapping.path(), "Placeholder value for '" + mapping.path() + "' is used; replace it before deployment."));
        }
    }

    /**
     * Reports a missing required profile parameter for a selected feature.
     *
     * @param feature owning feature.
     * @param mapping profile-value mapping.
     * @param omitted accumulating omitted mappings.
     * @param messages accumulating warnings.
     */
    private void handleMissingProfileValue(FeatureNode feature, ArtifactMapping mapping, List<OmittedMapping> omitted, List<GenerationMessage> messages) {
        if (!mapping.requiredWhenSelected()) {
            return;
        }
        omitted.add(new OmittedMapping(feature.id(), mapping.path(),
                "Required deployment profile parameter '" + mapping.valueFromProfile() + "' is not provided by the active profile."));
        messages.add(GenerationMessage.warning(feature.id(), mapping.path(),
                "Required value for '" + mapping.path() + "' is missing from the deployment profile; provide it before deployment."));
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
     * Adds warnings for any deprecated profile parameter keys still present in the active profile.
     *
     * @param profile active deployment profile.
     * @param messages accumulating warnings.
     */
    private void addDeprecatedAliasWarnings(DeploymentProfile profile, List<GenerationMessage> messages) {
        Map<String, String> deprecated = profileParameterResolver.deprecatedAliasesIn(profile.parameters());
        for (Map.Entry<String, String> alias : deprecated.entrySet()) {
            messages.add(GenerationMessage.warning(null, alias.getKey(),
                    "Deployment profile uses deprecated parameter key '" + alias.getKey() + "'; rename it to '" + alias.getValue() + "'."));
        }
    }

    /**
     * Converts a JSON toggle value to a typed Java value for the overlay writer.
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
