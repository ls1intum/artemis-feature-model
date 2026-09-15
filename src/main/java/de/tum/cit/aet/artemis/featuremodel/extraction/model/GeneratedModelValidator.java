package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

/**
 * Validates the assembled generated model through the same structural integrity rules the running app uses, plus the
 * model-side delivery rule that no technical feature may be visible or configurable for teachers. Everything the guided
 * workflow contributes is validated by the workflow stage validator.
 */
class GeneratedModelValidator {

    private static final String ROLE_TEACHER = "teacher";

    /**
     * Validation result.
     *
     * @param modelIntegrityValid whether the shared structural integrity rules passed.
     * @param deliveryEligible whether structural and role checks all passed.
     * @param items validation diagnostics for the extraction report.
     */
    record Result(boolean modelIntegrityValid, boolean deliveryEligible, List<ReportItem> items) {
    }

    /**
     * Validates the generated model.
     *
     * @param generatedModel assembled generated model.
     * @return model integrity state and report items.
     */
    Result validate(FeatureModel generatedModel) {
        List<ReportItem> items = new ArrayList<>();
        boolean modelIntegrityValid = validateModelIntegrity(generatedModel, items);
        validateRoleVisibility(generatedModel, items);
        boolean deliveryEligible = modelIntegrityValid && items.stream().noneMatch(item -> ReportItem.SEVERITY_ERROR.equals(item.severity()));
        return new Result(modelIntegrityValid, deliveryEligible, List.copyOf(items));
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
}
