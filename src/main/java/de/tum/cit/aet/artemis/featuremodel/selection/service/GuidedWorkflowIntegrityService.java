package de.tum.cit.aet.artemis.featuremodel.selection.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final String CODE_MISSING_GROUP_NODE = "MISSING_GUIDED_WORKFLOW_GROUP_NODE";

    private static final String CODE_UNKNOWN_GROUP_NODE = "UNKNOWN_GUIDED_WORKFLOW_GROUP_NODE";

    private static final String CODE_UNKNOWN_FEATURE = "UNKNOWN_GUIDED_WORKFLOW_FEATURE";

    private static final String CODE_MODEL_ID_MISMATCH = "GUIDED_WORKFLOW_MODEL_ID_MISMATCH";

    private static final String CODE_MODEL_VERSION_MISMATCH = "GUIDED_WORKFLOW_MODEL_VERSION_MISMATCH";

    /**
     * Validates a guided workflow against the active feature model. Only hard reference problems throw: unknown
     * feature ids, unknown steps, unknown or non-group review group nodes, duplicate ids, a missing default template,
     * or a workflow that explicitly pins a different model.
     *
     * @param workflow guided workflow to validate.
     * @param featureModel feature model whose ids may be referenced by the workflow.
     * @throws FeatureModelIntegrityException if the workflow targets a different model, contains duplicate ids, or references unknown model concepts.
     */
    public void validate(GuidedWorkflow workflow, FeatureModel featureModel) {
        Map<String, FeatureNode> featuresById = featureModel.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        Set<String> featureIds = featuresById.keySet();
        Set<String> stepIds = uniqueStepIds(workflow.steps());
        Set<String> templateIds = uniqueTemplateIds(workflow.useCaseTemplates());

        validateModelMatch(workflow, featureModel);
        validateDefaultTemplate(workflow, templateIds);
        validateTemplateReferences(workflow.useCaseTemplates(), stepIds, featureIds);
        validateStepReferences(workflow.steps(), featureIds);
        validateReviewGroupNodes(workflow.finalReviewGroups(), featuresById);
    }

    /**
     * Checks that the workflow targets the active feature model. The id and version are only compared when both the
     * workflow and the model declare them: the lean authored workflow carries no pin (the serve-time enrichment stamps
     * the active model), while an imported snapshot workflow may still pin its generating model explicitly.
     *
     * @param workflow guided workflow to validate.
     * @param featureModel active feature model.
     * @throws FeatureModelIntegrityException if the workflow targets a different feature model id or version.
     */
    private void validateModelMatch(GuidedWorkflow workflow, FeatureModel featureModel) {
        String workflowModelId = workflow.workflow().featureModelId();
        String modelId = featureModel.model().id();
        if (workflowModelId != null && modelId != null && !workflowModelId.equals(modelId)) {
            throw new FeatureModelIntegrityException(CODE_MODEL_ID_MISMATCH,
                    "Guided workflow targets feature model id '" + workflowModelId + "' but the active model id is '" + modelId + "'.");
        }
        String workflowModelVersion = workflow.workflow().featureModelVersion();
        String modelVersion = featureModel.model().version();
        if (workflowModelVersion != null && modelVersion != null && !workflowModelVersion.equals(modelVersion)) {
            throw new FeatureModelIntegrityException(CODE_MODEL_VERSION_MISMATCH,
                    "Guided workflow targets feature model version '" + workflowModelVersion + "' but the active model version is '" + modelVersion + "'.");
        }
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
     * @param workflow guided workflow to validate.
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
     * Checks decision option feature references and decision and option id uniqueness.
     *
     * @param steps workflow steps to inspect.
     * @param featureIds known feature ids.
     * @throws FeatureModelIntegrityException if a decision option references an unknown feature.
     */
    private void validateStepReferences(List<GuidedWorkflowStep> steps, Set<String> featureIds) {
        for (GuidedWorkflowStep step : steps) {
            validateDecisionIds(step);
            for (GuidedDecision decision : step.decisions()) {
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
     * Checks that every review group declares a group node that exists in the model and is a structural root or group
     * node, and that no group node is referenced twice. The member feature ids are no longer authored; the serve-time
     * enrichment derives them from the group node children, so referencing a valid group node is the whole contract.
     *
     * @param reviewGroups review groups to inspect.
     * @param featuresById known model features keyed by id.
     * @throws FeatureModelIntegrityException if a review group misses its group node reference or references an unknown or non-structural node.
     */
    private void validateReviewGroupNodes(List<FinalReviewGroup> reviewGroups, Map<String, FeatureNode> featuresById) {
        Set<String> groupNodeIds = new HashSet<>();
        for (FinalReviewGroup reviewGroup : reviewGroups) {
            String groupNodeId = reviewGroup.groupNodeId();
            if (groupNodeId == null || groupNodeId.isBlank()) {
                throw new FeatureModelIntegrityException(CODE_MISSING_GROUP_NODE, "A guided workflow review group does not declare a groupNodeId.");
            }
            addUniqueId(groupNodeIds, groupNodeId, "review group node", CODE_DUPLICATE_WORKFLOW_ID);
            FeatureNode groupNode = featuresById.get(groupNodeId);
            if (groupNode == null || !(groupNode.isGroup() || groupNode.isRoot())) {
                throw new FeatureModelIntegrityException(CODE_UNKNOWN_GROUP_NODE,
                        "Guided workflow review group references '" + groupNodeId + "' which is not a group node of the active model.");
            }
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
