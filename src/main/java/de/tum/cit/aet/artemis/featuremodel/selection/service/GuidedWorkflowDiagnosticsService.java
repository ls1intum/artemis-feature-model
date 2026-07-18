package de.tum.cit.aet.artemis.featuremodel.selection.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;

/**
 * Produces soft diagnostics about the guided workflow that must never fail a request: coverage of the selectable
 * functional features by guided decisions, validity of the capability ids the model references, template consistency,
 * and unfinished scaffold prose. Hard reference errors stay with {@link GuidedWorkflowIntegrityService} and keep
 * throwing; the findings here are a warning channel shared between the running app and the extraction pipeline's
 * generation report, so a coverage gap surfaces in both without ever turning into an HTTP 500.
 */
@Service
public class GuidedWorkflowDiagnosticsService {

    private static final String CATEGORY_FUNCTIONAL = "functional";

    /** Sentinel prefix that marks scaffold-generated prose awaiting a human author. */
    private static final String STUB_PROSE_PREFIX = "TODO";

    /**
     * Collects coverage, template-consistency, and stub-prose findings for a workflow against a model.
     *
     * @param workflow guided workflow, lean or enriched.
     * @param featureModel feature model to check coverage against.
     * @return findings in deterministic order; empty when the workflow fully covers the model.
     */
    public List<GuidedWorkflowFinding> findings(GuidedWorkflow workflow, FeatureModel featureModel) {
        List<GuidedWorkflowFinding> findings = new ArrayList<>();
        addCoverageFindings(workflow, featureModel, findings);
        addReviewGroupFindings(workflow, featureModel, findings);
        addTemplateFindings(workflow, findings);
        addStubProseFindings(workflow, findings);
        return List.copyOf(findings);
    }

    /**
     * Collects all findings including capability validity against a known capability universe.
     *
     * @param workflow guided workflow, lean or enriched.
     * @param featureModel feature model to check coverage against.
     * @param knownCapabilities union of the capability ids any known deployment profile provides.
     * @return findings in deterministic order; empty when the workflow and model are fully consistent.
     */
    public List<GuidedWorkflowFinding> findings(GuidedWorkflow workflow, FeatureModel featureModel, Set<String> knownCapabilities) {
        List<GuidedWorkflowFinding> findings = new ArrayList<>(findings(workflow, featureModel));
        addCapabilityFindings(featureModel, knownCapabilities, findings);
        return List.copyOf(findings);
    }

    /**
     * Warns for every selectable functional feature that no guided decision option selects. An uncovered feature is
     * silently invisible in the guided flow while still being validated and exported, which is exactly the silent
     * breakage this check surfaces.
     *
     * @param workflow guided workflow.
     * @param featureModel feature model.
     * @param findings finding sink.
     */
    private void addCoverageFindings(GuidedWorkflow workflow, FeatureModel featureModel, List<GuidedWorkflowFinding> findings) {
        Set<String> coveredFeatureIds = selectedFeatureIds(workflow);
        for (FeatureNode feature : featureModel.features()) {
            if (isGuidedEligible(feature) && !coveredFeatureIds.contains(feature.id())) {
                findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_COVERAGE_GAP, feature.id(),
                        "Selectable functional feature '" + feature.id() + "' is not selected by any guided decision option."));
            }
        }
    }

    /**
     * Warns for every model group whose selectable functional children are not represented by a final review group.
     *
     * @param workflow guided workflow.
     * @param featureModel feature model.
     * @param findings finding sink.
     */
    private void addReviewGroupFindings(GuidedWorkflow workflow, FeatureModel featureModel, List<GuidedWorkflowFinding> findings) {
        Set<String> reviewedGroupNodeIds = new LinkedHashSet<>();
        for (FinalReviewGroup reviewGroup : workflow.finalReviewGroups()) {
            reviewedGroupNodeIds.add(reviewGroup.groupNodeId());
        }
        for (FeatureNode feature : featureModel.features()) {
            if (!feature.isGroup() || reviewedGroupNodeIds.contains(feature.id())) {
                continue;
            }
            if (hasGuidedEligibleChild(feature.id(), featureModel)) {
                findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_REVIEW_GROUP_GAP, feature.id(),
                        "Model group '" + feature.id() + "' has selectable functional features but no final review group."));
            }
        }
    }

    /**
     * Warns for every capability id a model feature requires that no known deployment profile provides. A typo in a
     * capability id silently disables the feature under every profile, so an unknown id is always worth surfacing.
     *
     * @param featureModel feature model.
     * @param knownCapabilities union of provided capability ids.
     * @param findings finding sink.
     */
    private void addCapabilityFindings(FeatureModel featureModel, Set<String> knownCapabilities, List<GuidedWorkflowFinding> findings) {
        for (FeatureNode feature : featureModel.features()) {
            for (String capability : feature.requiresCapabilities()) {
                if (!knownCapabilities.contains(capability)) {
                    findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_UNKNOWN_CAPABILITY, capability,
                            "Feature '" + feature.id() + "' requires capability '" + capability + "' which no known deployment profile provides."));
                }
            }
        }
    }

    /**
     * Warns for templates that both select and deselect a feature, and for a default template that carries preset
     * selections instead of deferring to the backend-derived default selection.
     *
     * @param workflow guided workflow.
     * @param findings finding sink.
     */
    private void addTemplateFindings(GuidedWorkflow workflow, List<GuidedWorkflowFinding> findings) {
        for (UseCaseTemplate template : workflow.useCaseTemplates()) {
            for (String featureId : template.selectedFeatureIds()) {
                if (template.deselectedFeatureIds().contains(featureId)) {
                    findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_TEMPLATE_CONFLICT, template.id(),
                            "Template '" + template.id() + "' both selects and deselects feature '" + featureId + "'."));
                }
            }
            boolean isDefaultTemplate = template.id().equals(workflow.workflow().defaultTemplateId());
            if (isDefaultTemplate && !(template.selectedFeatureIds().isEmpty() && template.deselectedFeatureIds().isEmpty())) {
                findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_DEFAULT_TEMPLATE_PRESET, template.id(),
                        "Default template '" + template.id() + "' presets feature selections instead of deferring to the model defaults."));
            }
        }
    }

    /**
     * Warns for options whose prose still carries the scaffold TODO sentinel, so generated stubs keep surfacing until
     * a human writes the teacher-facing text.
     *
     * @param workflow guided workflow.
     * @param findings finding sink.
     */
    private void addStubProseFindings(GuidedWorkflow workflow, List<GuidedWorkflowFinding> findings) {
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (hasStubProse(option)) {
                        findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_STUB_PROSE, option.id(),
                                "Option '" + option.id() + "' still carries scaffold TODO prose and needs authored text."));
                    }
                }
            }
        }
    }

    /**
     * Collects every feature id selected by at least one guided decision option.
     *
     * @param workflow guided workflow.
     * @return selected feature ids.
     */
    private Set<String> selectedFeatureIds(GuidedWorkflow workflow) {
        Set<String> selected = new LinkedHashSet<>();
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    selected.addAll(option.selects());
                }
            }
        }
        return selected;
    }

    /**
     * Checks whether a feature belongs to the guided teacher surface: selectable and categorized functional.
     * Technical features are deliberately outside the guided workflow and never count as coverage gaps.
     *
     * @param feature feature node.
     * @return true if guided decisions are expected to cover the feature.
     */
    private boolean isGuidedEligible(FeatureNode feature) {
        return feature.selectable() && CATEGORY_FUNCTIONAL.equals(feature.category());
    }

    /**
     * Checks whether a group node has at least one guided-eligible direct child.
     *
     * @param groupNodeId group node id.
     * @param featureModel feature model.
     * @return true if a selectable functional child exists.
     */
    private boolean hasGuidedEligibleChild(String groupNodeId, FeatureModel featureModel) {
        for (FeatureRelation relation : featureModel.relations()) {
            if (!relation.parentId().equals(groupNodeId)) {
                continue;
            }
            for (FeatureNode feature : featureModel.features()) {
                if (feature.id().equals(relation.childId()) && isGuidedEligible(feature)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether any prose field of an option starts with the scaffold TODO sentinel.
     *
     * @param option decision option.
     * @return true if the option carries stub prose.
     */
    private boolean hasStubProse(GuidedDecisionOption option) {
        if (option.description() != null && option.description().startsWith(STUB_PROSE_PREFIX)) {
            return true;
        }
        return List.of(option.enabledOutcome(), option.recommendedWhen(), option.thingsToKnow(), option.warnings()).stream()
                .flatMap(List::stream).anyMatch(text -> text.startsWith(STUB_PROSE_PREFIX));
    }
}
