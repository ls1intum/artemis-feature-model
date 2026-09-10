package de.tum.cit.aet.artemis.featuremodel.selection.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies that the serve-time enrichment reproduces the wiring the authored workflow used to duplicate from the
 * model: the derived capability requirements, artifact impacts, review group members, and stamped model pin equal the
 * values the pre-deduplication resource carried for the bundled model and workflow.
 */
class GuidedWorkflowAssemblerTest {

    private final GuidedWorkflowAssembler assembler = new GuidedWorkflowAssembler();

    @Test
    void stampsWorkflowMetadataFromTheActiveModel() {
        GuidedWorkflow enriched = enrichedBundledWorkflow();

        assertThat(enriched.workflow().featureModelId()).isEqualTo("artemis-generated-feature-model");
        assertThat(enriched.workflow().featureModelVersion()).startsWith("0.1.0+");
        assertThat(enriched.workflow().id()).isEqualTo("artemis-guided-configuration");
    }

    @Test
    void derivesOptionCapabilitiesFromSelectedModelFeatures() {
        GuidedWorkflow enriched = enrichedBundledWorkflow();

        assertThat(findOption(enriched, "enable-iris").requiresCapabilities()).containsExactly("pyris-service", "pyris-secret");
        assertThat(findOption(enriched, "enable-athena").requiresCapabilities()).containsExactly("athena-service", "athena-secret");
        assertThat(findOption(enriched, "enable-hyperion").requiresCapabilities()).containsExactly("hyperion-service");
        assertThat(findOption(enriched, "enable-lti").requiresCapabilities()).containsExactly("lti-platform-registration");
        assertThat(findOption(enriched, "enable-theia").requiresCapabilities()).containsExactly("theia-service");
        assertThat(findOption(enriched, "enable-apollon").requiresCapabilities()).containsExactly("apollon-conversion-service");
        assertThat(findOption(enriched, "enable-sharing").requiresCapabilities()).containsExactly("sharing-platform-registration", "sharing-secret");
        assertThat(findOption(enriched, "enable-lecture-materials").requiresCapabilities()).isEmpty();
        assertThat(findOption(enriched, "keep-core-course-workflow").requiresCapabilities()).isEmpty();
    }

    @Test
    void derivesArtifactImpactsFromToggleMappings() {
        GuidedWorkflow enriched = enrichedBundledWorkflow();

        assertThat(findOption(enriched, "enable-lecture-materials").artifactImpacts())
                .containsExactly("Sets artemis.lecture.enabled = true in the generated external configuration overlay.");
        assertThat(findOption(enriched, "enable-written-exercise-types").artifactImpacts()).containsExactly(
                "Sets artemis.text.enabled = true in the generated external configuration overlay.",
                "Sets artemis.modeling.enabled = true in the generated external configuration overlay.",
                "Sets artemis.fileupload.enabled = true in the generated external configuration overlay.");
        assertThat(findOption(enriched, "enable-iris").artifactImpacts())
                .containsExactly("Sets artemis.iris.enabled = true in the generated external configuration overlay.");
        // Options selecting features without toggle mappings derive no impact sentences.
        assertThat(findOption(enriched, "keep-core-course-workflow").artifactImpacts()).isEmpty();
        assertThat(findOption(enriched, "enable-programming-and-quiz").artifactImpacts()).isEmpty();
    }

    @Test
    void derivesReviewGroupMembersFromModelGroupChildren() {
        FeatureModel model = bundledModel();
        GuidedWorkflow enriched = assembler.enrich(bundledWorkflow(), model);

        // The set of review groups, their order and their titles is authored workflow structure, so it stays pinned.
        assertThat(enriched.finalReviewGroups()).extracting(FinalReviewGroup::id).containsExactly("teaching-and-content", "exercise-system",
                "assessment-and-integrity", "adaptive-learning-and-ai", "platform-integrations");
        assertThat(enriched.finalReviewGroups()).extracting(FinalReviewGroup::title).containsExactly("Teaching Content", "Exercise Types",
                "Assessment and Integrity", "AI and Adaptive Learning", "Platform Integrations");

        // The members are model-derived, so assert the invariant rather than a snapshot that breaks whenever a feature
        // moves into or out of a group: each group holds exactly the direct children of its model node, in relation order.
        for (FinalReviewGroup group : enriched.finalReviewGroups()) {
            assertThat(group.featureIds()).as("members of review group '%s'", group.id())
                    .containsExactlyElementsOf(orderedChildFeatureIds(model, group.groupNodeId()));
        }
    }

    private GuidedWorkflow enrichedBundledWorkflow() {
        return assembler.enrich(bundledWorkflow(), bundledModel());
    }

    private FeatureModel bundledModel() {
        return new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper()).loadActiveModel();
    }

    private GuidedWorkflow bundledWorkflow() {
        return new JsonGuidedWorkflowStore(new DefaultResourceLoader(), new ObjectMapper()).loadActiveWorkflow();
    }

    private static List<String> orderedChildFeatureIds(FeatureModel model, String parentId) {
        return model.relations().stream().filter(relation -> relation.parentId().equals(parentId)).sorted(Comparator.comparingInt(FeatureRelation::order))
                .map(FeatureRelation::childId).toList();
    }

    private GuidedDecisionOption findOption(GuidedWorkflow workflow, String optionId) {
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (option.id().equals(optionId)) {
                        return option;
                    }
                }
            }
        }
        throw new AssertionError("Missing guided workflow option '" + optionId + "'.");
    }

}
