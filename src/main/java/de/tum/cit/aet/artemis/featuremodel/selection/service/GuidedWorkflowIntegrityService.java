package de.tum.cit.aet.artemis.featuremodel.selection.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

@Service
public class GuidedWorkflowIntegrityService {

    private static final String CODE_DUPLICATE_WORKFLOW_ID = "DUPLICATE_GUIDED_WORKFLOW_ID";

    private static final String CODE_MISSING_DEFAULT_TEMPLATE = "MISSING_GUIDED_WORKFLOW_DEFAULT_TEMPLATE";

    private static final String CODE_UNKNOWN_STEP = "UNKNOWN_GUIDED_WORKFLOW_STEP";

    private static final String CODE_UNKNOWN_REVIEW_GROUP = "UNKNOWN_GUIDED_WORKFLOW_REVIEW_GROUP";

    private static final String CODE_UNKNOWN_FEATURE = "UNKNOWN_GUIDED_WORKFLOW_FEATURE";

    /**
     * Validates a guided workflow against the active feature model.
     *
     * @param workflow guided workflow to validate.
     * @param featureModel feature model whose ids may be referenced by the workflow.
     * @throws FeatureModelIntegrityException if the workflow contains duplicate ids or references unknown model concepts.
     */
    public void validate(GuidedWorkflow workflow, FeatureModel featureModel) {
        Set<String> featureIds = featureIds(featureModel);
        Set<String> stepIds = uniqueStepIds(workflow.steps());
        Set<String> reviewGroupIds = uniqueReviewGroupIds(workflow.finalReviewGroups());
        Set<String> templateIds = uniqueTemplateIds(workflow.useCaseTemplates());

        validateDefaultTemplate(workflow, templateIds);
        validateTemplateReferences(workflow.useCaseTemplates(), stepIds, featureIds);
        validateStepReferences(workflow.steps(), reviewGroupIds, featureIds);
        validateReviewGroupReferences(workflow.finalReviewGroups(), featureIds);
    }

    /**
     * Collects known feature ids from the feature model.
     *
     * @param featureModel model to inspect.
     * @return known feature ids.
     */
    private Set<String> featureIds(FeatureModel featureModel) {
        Set<String> featureIds = new HashSet<>();
        for (FeatureNode feature : featureModel.features()) {
            featureIds.add(feature.id());
        }
        return featureIds;
    }

    /**
     * Collects unique workflow step ids.
     *
     * @param steps workflow steps to inspect.
     * @return known step ids.
     * @throws FeatureModelIntegrityException if a step id is duplicated.
     */
    private Set<String> uniqueStepIds(List<GuidedWorkflowStep> steps) {
        Set<String> stepIds = new HashSet<>();
        for (GuidedWorkflowStep step : steps) {
            addUniqueId(stepIds, step.id(), "step", CODE_DUPLICATE_WORKFLOW_ID);
        }
        return stepIds;
    }

    /**
     * Collects unique final review group ids.
     *
     * @param reviewGroups review groups to inspect.
     * @return known review group ids.
     * @throws FeatureModelIntegrityException if a review group id is duplicated.
     */
    private Set<String> uniqueReviewGroupIds(List<FinalReviewGroup> reviewGroups) {
        Set<String> reviewGroupIds = new HashSet<>();
        for (FinalReviewGroup reviewGroup : reviewGroups) {
            addUniqueId(reviewGroupIds, reviewGroup.id(), "review group", CODE_DUPLICATE_WORKFLOW_ID);
        }
        return reviewGroupIds;
    }

    /**
     * Collects unique use-case template ids.
     *
     * @param templates use-case templates to inspect.
     * @return known template ids.
     * @throws FeatureModelIntegrityException if a template id is duplicated.
     */
    private Set<String> uniqueTemplateIds(List<UseCaseTemplate> templates) {
        Set<String> templateIds = new HashSet<>();
        for (UseCaseTemplate template : templates) {
            addUniqueId(templateIds, template.id(), "template", CODE_DUPLICATE_WORKFLOW_ID);
        }
        return templateIds;
    }

    /**
     * Adds an id to a set and raises a stable integrity failure for duplicates.
     *
     * @param knownIds set of known ids.
     * @param id id to add.
     * @param kind user-facing id kind.
     * @param code stable error code.
     * @throws FeatureModelIntegrityException if the id is duplicated.
     */
    private void addUniqueId(Set<String> knownIds, String id, String kind, String code) {
        if (!knownIds.add(id)) {
            throw new FeatureModelIntegrityException(code, "Guided workflow " + kind + " id '" + id + "' is used more than once.");
        }
    }

    /**
     * Checks that the configured default template exists.
     *
     * @param workflow workflow metadata and content.
     * @param templateIds known template ids.
     * @throws FeatureModelIntegrityException if the default template is unknown.
     */
    private void validateDefaultTemplate(GuidedWorkflow workflow, Set<String> templateIds) {
        String defaultTemplateId = workflow.workflow().defaultTemplateId();
        if (!templateIds.contains(defaultTemplateId)) {
            throw new FeatureModelIntegrityException(CODE_MISSING_DEFAULT_TEMPLATE,
                    "Default guided workflow template '" + defaultTemplateId + "' does not exist.");
        }
    }

    /**
     * Checks template step and feature references.
     *
     * @param templates use-case templates to inspect.
     * @param stepIds known workflow step ids.
     * @param featureIds known feature ids.
     * @throws FeatureModelIntegrityException if a template references an unknown step or feature.
     */
    private void validateTemplateReferences(List<UseCaseTemplate> templates, Set<String> stepIds, Set<String> featureIds) {
        for (UseCaseTemplate template : templates) {
            validateStepIds(template.recommendedStepIds(), stepIds, "template '" + template.id() + "'");
            validateFeatureIds(template.selectedFeatureIds(), featureIds, "template '" + template.id() + "' selectedFeatureIds");
            validateFeatureIds(template.deselectedFeatureIds(), featureIds, "template '" + template.id() + "' deselectedFeatureIds");
        }
    }

    /**
     * Checks decision option feature references and review group references.
     *
     * @param steps workflow steps to inspect.
     * @param reviewGroupIds known review group ids.
     * @param featureIds known feature ids.
     * @throws FeatureModelIntegrityException if a decision references an unknown review group or feature.
     */
    private void validateStepReferences(List<GuidedWorkflowStep> steps, Set<String> reviewGroupIds, Set<String> featureIds) {
        for (GuidedWorkflowStep step : steps) {
            validateDecisionIds(step);
            for (GuidedDecision decision : step.decisions()) {
                validateReviewGroupId(decision.reviewGroupId(), reviewGroupIds, "decision '" + decision.id() + "'");
                validateOptionIds(decision);
                for (GuidedDecisionOption option : decision.options()) {
                    validateFeatureIds(option.selects(), featureIds, "option '" + option.id() + "' selects");
                    validateFeatureIds(option.deselects(), featureIds, "option '" + option.id() + "' deselects");
                }
            }
        }
    }

    /**
     * Checks that decision ids are unique within a step.
     *
     * @param step step to inspect.
     * @throws FeatureModelIntegrityException if a decision id is duplicated.
     */
    private void validateDecisionIds(GuidedWorkflowStep step) {
        Set<String> decisionIds = new HashSet<>();
        for (GuidedDecision decision : step.decisions()) {
            addUniqueId(decisionIds, decision.id(), "decision", CODE_DUPLICATE_WORKFLOW_ID);
        }
    }

    /**
     * Checks that option ids are unique within a decision.
     *
     * @param decision decision to inspect.
     * @throws FeatureModelIntegrityException if an option id is duplicated.
     */
    private void validateOptionIds(GuidedDecision decision) {
        Set<String> optionIds = new HashSet<>();
        for (GuidedDecisionOption option : decision.options()) {
            addUniqueId(optionIds, option.id(), "option", CODE_DUPLICATE_WORKFLOW_ID);
        }
    }

    /**
     * Checks final review group feature references.
     *
     * @param reviewGroups review groups to inspect.
     * @param featureIds known feature ids.
     * @throws FeatureModelIntegrityException if a review group references an unknown feature.
     */
    private void validateReviewGroupReferences(List<FinalReviewGroup> reviewGroups, Set<String> featureIds) {
        for (FinalReviewGroup reviewGroup : reviewGroups) {
            validateFeatureIds(reviewGroup.featureIds(), featureIds, "review group '" + reviewGroup.id() + "'");
        }
    }

    /**
     * Checks that all referenced step ids exist.
     *
     * @param referencedStepIds referenced step ids.
     * @param knownStepIds known step ids.
     * @param owner owner text for the exception message.
     * @throws FeatureModelIntegrityException if a step id is unknown.
     */
    private void validateStepIds(List<String> referencedStepIds, Set<String> knownStepIds, String owner) {
        for (String stepId : referencedStepIds) {
            if (!knownStepIds.contains(stepId)) {
                throw new FeatureModelIntegrityException(CODE_UNKNOWN_STEP, "Guided workflow " + owner + " references unknown step '" + stepId + "'.");
            }
        }
    }

    /**
     * Checks that a review group id exists.
     *
     * @param reviewGroupId referenced review group id.
     * @param knownReviewGroupIds known review group ids.
     * @param owner owner text for the exception message.
     * @throws FeatureModelIntegrityException if the review group id is unknown.
     */
    private void validateReviewGroupId(String reviewGroupId, Set<String> knownReviewGroupIds, String owner) {
        if (!knownReviewGroupIds.contains(reviewGroupId)) {
            throw new FeatureModelIntegrityException(CODE_UNKNOWN_REVIEW_GROUP,
                    "Guided workflow " + owner + " references unknown review group '" + reviewGroupId + "'.");
        }
    }

    /**
     * Checks that all referenced feature ids exist in the active model.
     *
     * @param referencedFeatureIds referenced feature ids.
     * @param knownFeatureIds known feature ids.
     * @param owner owner text for the exception message.
     * @throws FeatureModelIntegrityException if a feature id is unknown.
     */
    private void validateFeatureIds(List<String> referencedFeatureIds, Set<String> knownFeatureIds, String owner) {
        for (String featureId : referencedFeatureIds) {
            if (!knownFeatureIds.contains(featureId)) {
                throw new FeatureModelIntegrityException(CODE_UNKNOWN_FEATURE,
                        "Guided workflow " + owner + " references unknown feature '" + featureId + "'.");
            }
        }
    }
}
