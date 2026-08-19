package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.WorkflowValidationOutcome;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;

/** Covers the workflow-side validation rules: effective-workflow reference integrity and the graded findings gate. */
class GuidedWorkflowValidatorTest {

    private final GuidedWorkflowValidator validator = new GuidedWorkflowValidator();

    @Test
    void passesForAWorkflowThatCoversTheGeneratedModel() {
        WorkflowValidationOutcome result = validator.validate(model(), coveringWorkflow(), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.items()).isEmpty();
        assertThat(result.workflowIntegrityValid()).isTrue();
        assertThat(result.guidedValidation().status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_PASS);
        assertThat(result.guidedValidation().findings()).isEmpty();
        assertThat(result.deliveryEligible()).isTrue();
    }

    @Test
    void reportsWorkflowReferencingUnknownFeatureAsHardError() {
        GuidedDecisionOption unknown = completeOption("enable-ghost", List.of("ghost"), GuidedDecisionOption.STATUS_PUBLISHED);

        WorkflowValidationOutcome result = validator.validate(model(), workflow(List.of(unknown)), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_GENERATED_WORKFLOW_INVALID);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("ghost");
        });
        assertThat(result.workflowIntegrityValid()).isFalse();
        assertThat(result.deliveryEligible()).isFalse();
    }

    @Test
    void coverageGapWarningKeepsTheRunDeliveryEligible() {
        GuidedDecisionOption partial = completeOption("enable-nothing", List.of(), GuidedDecisionOption.STATUS_PUBLISHED);

        WorkflowValidationOutcome result = validator.validate(model(), workflow(List.of(partial)), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.guidedValidation().status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_FINDINGS);
        assertThat(result.guidedValidation().findings()).extracting(GuidedWorkflowFinding::code).contains(GuidedWorkflowFinding.CODE_COVERAGE_GAP);
        assertThat(result.guidedValidation().codeCounts()).containsKey(GuidedWorkflowFinding.CODE_COVERAGE_GAP);
        assertThat(result.guidedValidation().severityCounts()).containsEntry(GuidedWorkflowFinding.SEVERITY_WARNING, 1).doesNotContainKey(
                GuidedWorkflowFinding.SEVERITY_ERROR);
        assertThat(result.guidedValidation().deliveryEligible()).isTrue();
        assertThat(result.deliveryEligible()).isTrue();
        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
        });
        assertThat(result.workflowIntegrityValid()).isTrue();
        // Technical features never count as coverage gaps.
        assertThat(result.guidedValidation().findings()).extracting(GuidedWorkflowFinding::subject).doesNotContain("tech-a");
    }

    @Test
    void publishedOptionWithTodoProseBlocksDelivery() {
        GuidedDecisionOption todo = new GuidedDecisionOption("enable-alpha", "Alpha", "TODO: describe this option.", List.of("alpha"), List.of(), null, null,
                List.of("Outcome."), List.of("Fits."), List.of("Notes."), List.of(), GuidedDecisionOption.STATUS_PUBLISHED);

        WorkflowValidationOutcome result = validator.validate(model(), workflow(List.of(todo)), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.guidedValidation().findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(GuidedWorkflowFinding.CODE_STUB_PROSE);
            assertThat(finding.severity()).isEqualTo(GuidedWorkflowFinding.SEVERITY_ERROR);
        });
        assertThat(result.deliveryEligible()).isFalse();
        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        // The projection removes the incomplete option before reference validation, so integrity still passes.
        assertThat(result.workflowIntegrityValid()).isTrue();
    }

    @Test
    void draftReferencingUnknownFeatureWarnsWithoutBlocking() {
        GuidedDecisionOption published = completeOption("enable-alpha", List.of("alpha"), GuidedDecisionOption.STATUS_PUBLISHED);
        GuidedDecisionOption draft = completeOption("enable-ghost-draft", List.of("ghost"), GuidedDecisionOption.STATUS_DRAFT);

        WorkflowValidationOutcome result = validator.validate(model(), workflow(List.of(published, draft)),
                profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.workflowIntegrityValid()).isTrue();
        assertThat(result.deliveryEligible()).isTrue();
        assertThat(result.guidedValidation().findings())
                .filteredOn(finding -> finding.code().equals(GuidedWorkflowFinding.CODE_DRAFT_UNKNOWN_REFERENCE))
                .allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(GuidedWorkflowFinding.SEVERITY_WARNING))
                .extracting(GuidedWorkflowFinding::subject).containsExactly("enable-ghost-draft");
    }

    @Test
    void draftOnlyCoverageStillCountsAsACoverageGap() {
        GuidedDecisionOption draft = completeOption("enable-alpha-draft", List.of("alpha"), GuidedDecisionOption.STATUS_DRAFT);

        WorkflowValidationOutcome result = validator.validate(model(), workflow(List.of(draft)), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.guidedValidation().findings()).extracting(GuidedWorkflowFinding::code).contains(GuidedWorkflowFinding.CODE_COVERAGE_GAP,
                GuidedWorkflowFinding.CODE_DRAFT_OPTION);
        assertThat(result.deliveryEligible()).isTrue();
    }

    private FeatureModel model() {
        FeatureNode root = new FeatureNode("root", "Root", "root", false, null, "not_applicable", null);
        FeatureNode group = new FeatureNode("alpha-group", "Alpha Group", "group", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha", "Alpha", "module", true, null, "enabled", null, "functional", List.of("teacher", "maintainer"),
                List.of("teacher", "maintainer"), List.of("alpha-service"), null, null);
        FeatureNode technical = new FeatureNode("tech-a", "Tech A", "feature", true, null, "enabled", null, "technical", List.of("maintainer"),
                List.of("maintainer"), List.of(), null, null);
        List<FeatureRelation> relations = List.of(new FeatureRelation("root", "alpha-group", "group", "and", 1),
                new FeatureRelation("alpha-group", "alpha", "optional", null, 1), new FeatureRelation("root", "tech-a", "optional", null, 2));
        return new FeatureModel(new ModelMetadata("generated-test-model", "Generated Test Model", "0.0.1"), List.of(root, group, alpha, technical), relations,
                List.of());
    }

    private GuidedWorkflow coveringWorkflow() {
        return workflow(List.of(completeOption("enable-alpha", List.of("alpha"), GuidedDecisionOption.STATUS_PUBLISHED)));
    }

    private GuidedDecisionOption completeOption(String id, List<String> selects, String status) {
        return new GuidedDecisionOption(id, "Option " + id, "Synthetic option.", selects, List.of(), null, null, List.of("Outcome."), List.of("Fits."),
                List.of("Notes."), List.of(), status);
    }

    private GuidedWorkflow workflow(List<GuidedDecisionOption> options) {
        GuidedWorkflowMetadata metadata = new GuidedWorkflowMetadata("test-workflow", "Test Workflow", "0.0.1", null, null, "custom");
        UseCaseTemplate template = new UseCaseTemplate("custom", "Custom", "Synthetic template.", List.of(), List.of(), List.of(), List.of(), List.of());
        GuidedWorkflowStep step = new GuidedWorkflowStep("selection", "Selection", 1, "Synthetic step.",
                List.of(new GuidedDecision("decision", "Question?", "Synthetic decision.", "multiple", options)));
        FinalReviewGroup reviewGroup = new FinalReviewGroup(null, "alpha-group", "Summary", 1, null);
        return new GuidedWorkflow(metadata, List.of(template), List.of(step), List.of(reviewGroup));
    }

    private DeploymentProfile profile(List<String> providedCapabilities) {
        return new DeploymentProfile("test-profile", "Test Profile", "1.0.0", "published", List.of("maintainer"), providedCapabilities, null, null);
    }
}
