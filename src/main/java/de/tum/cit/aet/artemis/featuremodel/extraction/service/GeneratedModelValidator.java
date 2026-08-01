package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

/**
 * Validates the assembled generated model through the same structural integrity rules the running app uses, plus the
 * two model-side E3 rules: no technical feature may be visible or configurable for teachers, and every capability an
 * included technical feature provides must be listed by the bundled deployment profile. Everything the guided
 * workflow contributes is validated by {@link GuidedWorkflowValidator} in the workflow stage.
 */
class GeneratedModelValidator {

    private static final String ROLE_TEACHER = "teacher";

    /**
     * Validation result.
     *
     * @param modelIntegrityValid whether the generated model passed the shared structural integrity validation.
     * @param items validation diagnostics for the extraction report.
     */
    record Result(boolean modelIntegrityValid, List<ReportItem> items) {
    }

    /**
     * Validates the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param includedFeatures resolved include semantics carrying the manifest-declared provided capabilities.
     * @param bundledProfile bundled deployment profile.
     * @return model integrity state and report items.
     */
    Result validate(FeatureModel generatedModel, List<ResolvedFeatureScope> includedFeatures, DeploymentProfile bundledProfile) {
        List<ReportItem> items = new ArrayList<>();
        boolean modelIntegrityValid = validateModelIntegrity(generatedModel, items);
        validateRoleVisibility(generatedModel, items);
        validateProvidedCapabilities(includedFeatures, bundledProfile, items);
        return new Result(modelIntegrityValid, List.copyOf(items));
    }

    /**
     * Runs the shared structural integrity rules on the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param items diagnostics sink.
     * @return true when shared model integrity validation passes.
     */
    private boolean validateModelIntegrity(FeatureModel generatedModel, List<ReportItem> items) {
        try {
            new FeatureModelIntegrityService().validate(generatedModel);
            return true;
        }
        catch (FeatureModelIntegrityException e) {
            items.add(ReportItem.error(ReportItem.CODE_GENERATED_MODEL_INVALID, e.getCode(), "Generated model failed integrity validation: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Enforces the role-visibility rule: no technical feature may be visible to or configurable by teachers.
     *
     * @param generatedModel assembled generated model.
     * @param items diagnostics sink.
     */
    private void validateRoleVisibility(FeatureModel generatedModel, List<ReportItem> items) {
        for (FeatureNode feature : generatedModel.features()) {
            if (!FeatureScopeManifest.CATEGORY_TECHNICAL.equals(feature.category())) {
                continue;
            }
            if (feature.visibleTo().contains(ROLE_TEACHER) || feature.configurableBy().contains(ROLE_TEACHER)) {
                items.add(ReportItem.error(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK, feature.id(),
                        "Technical feature '" + feature.id() + "' is visible or configurable for teachers."));
            }
        }
    }

    /**
     * Cross-checks the capabilities included technical features provide against the bundled profile's provided
     * capabilities. A mismatch is a warning: the profile and the technical selection describe the same deployment
     * context and should agree. The model schema carries no provides list, so the check consumes the resolved
     * manifest declarations directly.
     *
     * @param includedFeatures resolved include semantics.
     * @param bundledProfile bundled deployment profile.
     * @param items diagnostics sink.
     */
    private void validateProvidedCapabilities(List<ResolvedFeatureScope> includedFeatures, DeploymentProfile bundledProfile, List<ReportItem> items) {
        for (ResolvedFeatureScope included : includedFeatures) {
            if (!FeatureScopeManifest.CATEGORY_TECHNICAL.equals(included.category())) {
                continue;
            }
            for (String capability : included.providesCapabilities()) {
                if (!bundledProfile.providesCapability(capability)) {
                    items.add(ReportItem.warning(ReportItem.CODE_PROFILE_CAPABILITY_MISMATCH, included.id(), "Technical feature '" + included.id()
                            + "' provides capability '" + capability + "' which the bundled profile '" + bundledProfile.id() + "' does not list."));
                }
            }
        }
    }
}
