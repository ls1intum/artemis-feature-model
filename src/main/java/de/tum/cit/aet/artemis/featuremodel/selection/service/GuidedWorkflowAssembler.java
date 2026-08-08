package de.tum.cit.aet.artemis.featuremodel.selection.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;

/**
 * Enriches the lean authored guided workflow with the wiring the feature model owns, so the served record keeps the
 * shape the client already consumes: option capability requirements are the union of the selected features'
 * {@code requiresCapabilities}, artifact impacts restate the selected features' toggle mappings, review group members
 * are the children of the referenced model group node, and the workflow metadata is stamped with the active model id
 * and version. Enrichment is a pure function of the loaded workflow and model and is recomputed on every call, so a
 * different active model never serves stale wiring.
 */
@Service
public class GuidedWorkflowAssembler {

    /** Overlay file whose toggle mappings are restated as user-visible artifact impact sentences. */
    private static final String OVERLAY_TARGET = "application-feature-model.yml";

    /**
     * Enriches a lean guided workflow against the active feature model.
     *
     * @param workflow lean authored guided workflow.
     * @param featureModel active feature model providing the derived wiring.
     * @return enriched guided workflow with the served shape.
     */
    public GuidedWorkflow enrich(GuidedWorkflow workflow, FeatureModel featureModel) {
        Map<String, FeatureNode> featuresById = featureModel.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        GuidedWorkflowMetadata metadata = stampedMetadata(workflow.workflow(), featureModel);
        List<GuidedWorkflowStep> steps = workflow.steps().stream().map(step -> enrichStep(step, featuresById)).toList();
        List<FinalReviewGroup> reviewGroups = enrichReviewGroups(workflow.finalReviewGroups(), featureModel, featuresById);
        return new GuidedWorkflow(metadata, workflow.useCaseTemplates(), steps, reviewGroups);
    }

    /**
     * Stamps the workflow metadata with the id and version of the active feature model.
     *
     * @param metadata authored workflow metadata.
     * @param featureModel active feature model.
     * @return metadata carrying the active model id and version.
     */
    private GuidedWorkflowMetadata stampedMetadata(GuidedWorkflowMetadata metadata, FeatureModel featureModel) {
        return new GuidedWorkflowMetadata(metadata.id(), metadata.name(), metadata.version(), featureModel.model().id(), featureModel.model().version(),
                metadata.defaultTemplateId());
    }

    /**
     * Enriches every decision option of a step.
     *
     * @param step authored workflow step.
     * @param featuresById model features keyed by id.
     * @return step with enriched options.
     */
    private GuidedWorkflowStep enrichStep(GuidedWorkflowStep step, Map<String, FeatureNode> featuresById) {
        List<GuidedDecision> decisions = new ArrayList<>();
        for (GuidedDecision decision : step.decisions()) {
            List<GuidedDecisionOption> options = decision.options().stream().map(option -> enrichOption(option, featuresById)).toList();
            decisions.add(new GuidedDecision(decision.id(), decision.question(), decision.description(), decision.selectionMode(), options));
        }
        return new GuidedWorkflowStep(step.id(), step.title(), step.order(), step.description(), decisions);
    }

    /**
     * Enriches one option with the capability requirements and artifact impacts of its selected features.
     *
     * @param option authored decision option.
     * @param featuresById model features keyed by id.
     * @return option with derived wiring populated.
     */
    private GuidedDecisionOption enrichOption(GuidedDecisionOption option, Map<String, FeatureNode> featuresById) {
        List<String> requiresCapabilities = derivedRequiredCapabilities(option, featuresById);
        List<String> artifactImpacts = derivedArtifactImpacts(option, featuresById);
        return new GuidedDecisionOption(option.id(), option.label(), option.description(), option.selects(), option.deselects(), requiresCapabilities,
                artifactImpacts, option.enabledOutcome(), option.recommendedWhen(), option.thingsToKnow(), option.warnings(), option.status());
    }

    /**
     * Derives the capability requirements of an option as the union of the selected features' capabilities, keeping
     * the order of the selects list and each feature's declaration order.
     *
     * @param option decision option.
     * @param featuresById model features keyed by id.
     * @return derived capability ids.
     */
    private List<String> derivedRequiredCapabilities(GuidedDecisionOption option, Map<String, FeatureNode> featuresById) {
        Set<String> capabilities = new LinkedHashSet<>();
        for (String featureId : option.selects()) {
            FeatureNode feature = featuresById.get(featureId);
            if (feature != null) {
                capabilities.addAll(feature.requiresCapabilities());
            }
        }
        return List.copyOf(capabilities);
    }

    /**
     * Derives the artifact impact sentences of an option from the toggle mappings of its selected features.
     *
     * @param option decision option.
     * @param featuresById model features keyed by id.
     * @return derived artifact impact sentences.
     */
    private List<String> derivedArtifactImpacts(GuidedDecisionOption option, Map<String, FeatureNode> featuresById) {
        List<String> impacts = new ArrayList<>();
        for (String featureId : option.selects()) {
            FeatureNode feature = featuresById.get(featureId);
            if (feature == null) {
                continue;
            }
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (mapping.isToggle() && mapping.valueWhenSelected() != null && OVERLAY_TARGET.equals(mapping.target())) {
                    impacts.add("Sets " + mapping.path() + " = " + mapping.valueWhenSelected().asString() + " in the generated external configuration overlay.");
                }
            }
        }
        return List.copyOf(impacts);
    }

    /**
     * Enriches the review groups with served ids, default titles and orders, and the member feature ids derived from
     * the referenced model group nodes.
     *
     * @param reviewGroups authored review groups.
     * @param featureModel active feature model.
     * @param featuresById model features keyed by id.
     * @return enriched review groups.
     */
    private List<FinalReviewGroup> enrichReviewGroups(List<FinalReviewGroup> reviewGroups, FeatureModel featureModel, Map<String, FeatureNode> featuresById) {
        List<FinalReviewGroup> enriched = new ArrayList<>();
        for (int index = 0; index < reviewGroups.size(); index++) {
            FinalReviewGroup group = reviewGroups.get(index);
            FeatureNode groupNode = featuresById.get(group.groupNodeId());
            String title = group.title() != null ? group.title() : groupNode.name();
            int order = group.order() > 0 ? group.order() : index + 1;
            enriched.add(new FinalReviewGroup(group.groupNodeId(), group.groupNodeId(), title, order, childFeatureIds(featureModel, group.groupNodeId())));
        }
        return List.copyOf(enriched);
    }

    /**
     * Collects the direct children of a model node in relation order.
     *
     * @param featureModel active feature model.
     * @param parentId parent node id.
     * @return child feature ids in relation order.
     */
    private List<String> childFeatureIds(FeatureModel featureModel, String parentId) {
        List<FeatureRelation> childRelations = new ArrayList<>();
        for (FeatureRelation relation : featureModel.relations()) {
            if (relation.parentId().equals(parentId)) {
                childRelations.add(relation);
            }
        }
        childRelations.sort(Comparator.comparingInt(FeatureRelation::order));
        return childRelations.stream().map(FeatureRelation::childId).toList();
    }
}
