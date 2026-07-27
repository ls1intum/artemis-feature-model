package de.tum.cit.aet.artemis.featuremodel.selection.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import tools.jackson.databind.ObjectMapper;

class GuidedWorkflowDiagnosticsServiceTest {

    private final GuidedWorkflowDiagnosticsService service = new GuidedWorkflowDiagnosticsService();

    @Test
    void bundledWorkflowAndModelProduceNoFindings() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModel model = new JsonFeatureModelStore(resourceLoader, objectMapper).loadActiveModel();
        GuidedWorkflow workflow = new JsonGuidedWorkflowStore(resourceLoader, objectMapper).loadActiveWorkflow();

        assertThat(service.findings(workflow, model)).isEmpty();
    }

    @Test
    void warnsForSelectableFunctionalFeatureWithoutGuidedCoverage() {
        // The synthetic workflow covers programming and quiz but not athena or exercise-common.
        GuidedWorkflow workflow = workflow(List.of(option("enable-core", List.of("programming", "quiz"))));

        List<GuidedWorkflowFinding> findings = service.findings(workflow, TestFeatureModels.baseModel());

        assertThat(findings).extracting(GuidedWorkflowFinding::code).contains(GuidedWorkflowFinding.CODE_COVERAGE_GAP);
        assertThat(findings).filteredOn(finding -> finding.code().equals(GuidedWorkflowFinding.CODE_COVERAGE_GAP))
                .extracting(GuidedWorkflowFinding::subject).containsExactly("exercise-common", "athena");
        assertThat(findings).allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(GuidedWorkflowFinding.SEVERITY_WARNING));
    }

    @Test
    void warnsForModelGroupWithoutReviewGroup() {
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(emptyDefaultTemplate()),
                List.of(step(option("enable-all", List.of("exercise-common", "programming", "quiz", "athena")))), List.of());

        List<GuidedWorkflowFinding> findings = service.findings(workflow, TestFeatureModels.baseModel());

        assertThat(findings).extracting(GuidedWorkflowFinding::code).containsExactly(GuidedWorkflowFinding.CODE_REVIEW_GROUP_GAP);
        assertThat(findings.getFirst().subject()).isEqualTo("exercise-system");
    }

    @Test
    void warnsForUnknownCapabilityId() {
        assertThat(service.findings(coveringWorkflow(), TestFeatureModels.baseModel(), Set.of("athena-service"))).isEmpty();

        FeatureModel modelWithTypo = TestFeatureModels.withFeatures(List.of(
                new FeatureNode("artemis", "Artemis", "root", false, null, "not_applicable", null),
                new FeatureNode("exercise-system", "Exercise System", "group", false, null, "not_applicable", null),
                new FeatureNode("exercise-common", "Exercise Common", "module", true, null, "enabled", null),
                new FeatureNode("programming", "Programming", "module", true, null, "enabled", null),
                new FeatureNode("quiz", "Quiz", "module", true, null, "enabled", null),
                new FeatureNode("athena", "Athena", "module", true, null, "disabled", null, null, null, null, List.of("athena-servcie"), null, null)));

        List<GuidedWorkflowFinding> typoFindings = service.findings(coveringWorkflow(), modelWithTypo, Set.of("athena-service"));

        assertThat(typoFindings).extracting(GuidedWorkflowFinding::code).containsExactly(GuidedWorkflowFinding.CODE_UNKNOWN_CAPABILITY);
        assertThat(typoFindings.getFirst().subject()).isEqualTo("athena-servcie");
    }

    @Test
    void warnsForTemplateSelectingAndDeselectingTheSameFeature() {
        UseCaseTemplate conflicted = new UseCaseTemplate("conflicted", "Conflicted", "Synthetic template.", List.of("athena"), List.of("athena"),
                List.of(), List.of(), List.of());
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(emptyDefaultTemplate(), conflicted),
                List.of(step(option("enable-all", List.of("exercise-common", "programming", "quiz", "athena")))), List.of(reviewGroup()));

        List<GuidedWorkflowFinding> findings = service.findings(workflow, TestFeatureModels.baseModel());

        assertThat(findings).extracting(GuidedWorkflowFinding::code).containsExactly(GuidedWorkflowFinding.CODE_TEMPLATE_CONFLICT);
        assertThat(findings.getFirst().subject()).isEqualTo("conflicted");
    }

    @Test
    void warnsForDefaultTemplateWithPresetSelections() {
        UseCaseTemplate presetDefault = new UseCaseTemplate("custom", "Custom", "Synthetic template.", List.of("athena"), List.of(), List.of(),
                List.of(), List.of());
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(presetDefault),
                List.of(step(option("enable-all", List.of("exercise-common", "programming", "quiz", "athena")))), List.of(reviewGroup()));

        List<GuidedWorkflowFinding> findings = service.findings(workflow, TestFeatureModels.baseModel());

        assertThat(findings).extracting(GuidedWorkflowFinding::code).containsExactly(GuidedWorkflowFinding.CODE_DEFAULT_TEMPLATE_PRESET);
    }

    @Test
    void warnsForScaffoldStubProse() {
        GuidedDecisionOption stub = new GuidedDecisionOption("enable-athena-stub", "Athena", "TODO: describe this option.",
                List.of("exercise-common", "programming", "quiz", "athena"), List.of(), null, null, List.of("TODO: describe the outcome."), List.of(),
                List.of(), List.of());
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(emptyDefaultTemplate()), List.of(step(stub)), List.of(reviewGroup()));

        List<GuidedWorkflowFinding> findings = service.findings(workflow, TestFeatureModels.baseModel());

        assertThat(findings).extracting(GuidedWorkflowFinding::code).containsExactly(GuidedWorkflowFinding.CODE_STUB_PROSE);
        assertThat(findings.getFirst().subject()).isEqualTo("enable-athena-stub");
    }

    private GuidedWorkflow coveringWorkflow() {
        return workflow(List.of(option("enable-all", List.of("exercise-common", "programming", "quiz", "athena"))));
    }

    private GuidedWorkflow workflow(List<GuidedDecisionOption> options) {
        GuidedWorkflowStep step = new GuidedWorkflowStep("selection", "Selection", 1, "Synthetic step.",
                List.of(new GuidedDecision("decision", "Question?", "Synthetic decision.", "multiple", options)));
        return new GuidedWorkflow(metadata(), List.of(emptyDefaultTemplate()), List.of(step), List.of(reviewGroup()));
    }

    private GuidedWorkflowStep step(GuidedDecisionOption option) {
        return new GuidedWorkflowStep("selection", "Selection", 1, "Synthetic step.",
                List.of(new GuidedDecision("decision", "Question?", "Synthetic decision.", "multiple", List.of(option))));
    }

    private GuidedDecisionOption option(String id, List<String> selects) {
        return new GuidedDecisionOption(id, "Option " + id, "Synthetic option.", selects, List.of(), null, null, List.of("Outcome."), List.of(), List.of(),
                List.of());
    }

    private GuidedWorkflowMetadata metadata() {
        return new GuidedWorkflowMetadata("test-guided-workflow", "Test Guided Workflow", "0.0.1", null, null, "custom");
    }

    private UseCaseTemplate emptyDefaultTemplate() {
        return new UseCaseTemplate("custom", "Custom", "Synthetic default template.", List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private FinalReviewGroup reviewGroup() {
        return new FinalReviewGroup(null, "exercise-system", "Summary", 1, null);
    }
}
