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
 * Produces graded diagnostics about the guided workflow that never fail a request in the running app: coverage of the
 * selectable functional features by published guided decisions, validity of the capability ids the model references,
 * template consistency, lifecycle states, and completeness of published prose. Hard reference errors stay with
 * {@link GuidedWorkflowIntegrityService} and keep throwing; the findings here are a shared channel between the running
 * app and the extraction pipeline's generation report. Severity is assigned together with the code: {@code error}
 * findings describe an invalid or semantically incomplete published contract and block snapshot delivery, while
 * {@code warning} findings describe incompleteness of the guided surface that publishes.
 */
@Service
public class GuidedWorkflowDiagnosticsService {

    private static final String CATEGORY_FUNCTIONAL = "functional";

    private final GuidedWorkflowProjectionService projectionService = new GuidedWorkflowProjectionService();

    /**
     * Collects coverage, template-consistency, lifecycle, and published-completeness findings for a workflow against
     * a model.
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
        addPublishedCompletenessFindings(workflow, findings);
        addLifecycleFindings(workflow, featureModel, findings);
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
     * Warns for every selectable functional feature that no published guided decision option selects. Draft options
     * do not count as coverage: an uncovered or draft-only feature remains configurable in the tree, and the warning
     * keeps surfacing until its guided explanation is published.
     *
     * @param workflow guided workflow.
     * @param featureModel feature model.
     * @param findings finding sink.
     */
    private void addCoverageFindings(GuidedWorkflow workflow, FeatureModel featureModel, List<GuidedWorkflowFinding> findings) {
        Set<String> coveredFeatureIds = publishedSelectedFeatureIds(workflow);
        for (FeatureNode feature : featureModel.features()) {
            if (isGuidedEligible(feature) && !coveredFeatureIds.contains(feature.id())) {
                findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_COVERAGE_GAP, feature.id(),
                        "Selectable functional feature '" + feature.id() + "' is not selected by any published guided decision option."));
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
     * Raises an error for every capability id a model feature requires that no known deployment profile provides. A
     * typo in a capability id silently disables the feature under every profile, so an unknown id blocks delivery.
     *
     * @param featureModel feature model.
     * @param knownCapabilities union of provided capability ids.
     * @param findings finding sink.
     */
    private void addCapabilityFindings(FeatureModel featureModel, Set<String> knownCapabilities, List<GuidedWorkflowFinding> findings) {
        for (FeatureNode feature : featureModel.features()) {
            for (String capability : feature.requiresCapabilities()) {
                if (!knownCapabilities.contains(capability)) {
                    findings.add(GuidedWorkflowFinding.error(GuidedWorkflowFinding.CODE_UNKNOWN_CAPABILITY, capability,
                            "Feature '" + feature.id() + "' requires capability '" + capability + "' which no known deployment profile provides."));
                }
            }
        }
    }

    /**
     * Raises errors for templates that both select and deselect a feature and for templates that preset a feature no
     * published option covers, and warns for a default template that carries preset selections instead of deferring
     * to the backend-derived default selection. A preset of a draft-only or uncovered feature would enable the
     * feature through the guided UI without its published explanation, so it blocks delivery.
     *
     * @param workflow guided workflow.
     * @param findings finding sink.
     */
    private void addTemplateFindings(GuidedWorkflow workflow, List<GuidedWorkflowFinding> findings) {
        Set<String> coveredFeatureIds = publishedSelectedFeatureIds(workflow);
        for (UseCaseTemplate template : workflow.useCaseTemplates()) {
            for (String featureId : template.selectedFeatureIds()) {
                if (template.deselectedFeatureIds().contains(featureId)) {
                    findings.add(GuidedWorkflowFinding.error(GuidedWorkflowFinding.CODE_TEMPLATE_CONFLICT, template.id(),
                            "Template '" + template.id() + "' both selects and deselects feature '" + featureId + "'."));
                }
                if (!coveredFeatureIds.contains(featureId)) {
                    findings.add(GuidedWorkflowFinding.error(GuidedWorkflowFinding.CODE_TEMPLATE_UNCOVERED_PRESET, template.id(),
                            "Template '" + template.id() + "' presets feature '" + featureId + "' which no published guided decision option covers."));
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
     * Raises an error for every published option whose required prose is incomplete: blank or TODO label,
     * description, enabled outcome, recommendation, or things-to-know entries, or an incomplete decision question or
     * description. The runtime projection defensively omits such options; extraction blocks the run so the gap is
     * fixed at the source.
     *
     * @param workflow guided workflow.
     * @param findings finding sink.
     */
    private void addPublishedCompletenessFindings(GuidedWorkflow workflow, List<GuidedWorkflowFinding> findings) {
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (!option.isDraft() && !projectionService.hasCompletePublishedProse(option, decision)) {
                        findings.add(GuidedWorkflowFinding.error(GuidedWorkflowFinding.CODE_STUB_PROSE, option.id(),
                                "Published option '" + option.id() + "' or its decision still carries TODO or empty required prose."));
                    }
                }
            }
        }
    }

    /**
     * Warns for options without an explicit lifecycle status, for existing draft options, and for draft options that
     * reference a feature unknown to the model. A draft typo is visible from the first run but never blocks one; it
     * hardens into a hard reference error the moment the option is published.
     *
     * @param workflow guided workflow.
     * @param featureModel feature model.
     * @param findings finding sink.
     */
    private void addLifecycleFindings(GuidedWorkflow workflow, FeatureModel featureModel, List<GuidedWorkflowFinding> findings) {
        Set<String> knownFeatureIds = new LinkedHashSet<>();
        featureModel.features().forEach(feature -> knownFeatureIds.add(feature.id()));
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (!option.hasExplicitStatus()) {
                        findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_MISSING_STATUS, option.id(),
                                "Option '" + option.id() + "' declares no lifecycle status and is treated as published."));
                    }
                    if (!option.isDraft()) {
                        continue;
                    }
                    findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_DRAFT_OPTION, option.id(),
                            "Option '" + option.id() + "' is a draft and is not served to any client."));
                    for (String featureId : referencedFeatureIds(option)) {
                        if (!knownFeatureIds.contains(featureId)) {
                            findings.add(GuidedWorkflowFinding.warning(GuidedWorkflowFinding.CODE_DRAFT_UNKNOWN_REFERENCE, option.id(),
                                    "Draft option '" + option.id() + "' references feature '" + featureId + "' which the model does not contain."));
                        }
                    }
                }
            }
        }
    }

    /**
     * Collects every feature id selected by at least one published guided decision option.
     *
     * @param workflow guided workflow.
     * @return feature ids covered by published options.
     */
    private Set<String> publishedSelectedFeatureIds(GuidedWorkflow workflow) {
        Set<String> selected = new LinkedHashSet<>();
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (!option.isDraft()) {
                        selected.addAll(option.selects());
                    }
                }
            }
        }
        return selected;
    }

    /**
     * Collects the feature ids an option selects or deselects, in declaration order.
     *
     * @param option decision option.
     * @return referenced feature ids.
     */
    private List<String> referencedFeatureIds(GuidedDecisionOption option) {
        List<String> referenced = new ArrayList<>(option.selects());
        referenced.addAll(option.deselects());
        return referenced;
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
}
