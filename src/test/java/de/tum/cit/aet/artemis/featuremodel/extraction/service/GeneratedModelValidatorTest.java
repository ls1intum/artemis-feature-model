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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;

/** Covers the E3 validation rules: role visibility, profile capability cross-check, guided integrity, and coverage. */
class GeneratedModelValidatorTest {

    private final GeneratedModelValidator validator = new GeneratedModelValidator();

    @Test
    void passesForConsistentModelWorkflowAndProfile() {
        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("maintainer"), List.of("maintainer"))), includes(),
                coveringWorkflow(), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.items()).isEmpty();
        assertThat(result.artifactValidation().snapshotEligible()).isTrue();
        assertThat(result.guidedValidation().status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_PASS);
        assertThat(result.guidedValidation().findings()).isEmpty();
    }

    @Test
    void marksInvalidGeneratedModelAsSnapshotIneligible() {
        FeatureModel valid = model(technicalFeature(List.of("maintainer"), List.of("maintainer")));
        FeatureModel withoutRoot = new FeatureModel(valid.model(), valid.features().stream().filter(feature -> !feature.isRoot()).toList(), valid.relations(),
                valid.constraints());

        GeneratedModelValidator.Result result = validator.validate(withoutRoot, includes(), coveringWorkflow(),
                profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.artifactValidation().modelIntegrityValid()).isFalse();
        assertThat(result.artifactValidation().snapshotEligible()).isFalse();
        assertThat(result.items()).anySatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_GENERATED_MODEL_INVALID));
    }

    @Test
    void reportsTechnicalFeatureVisibleToTeachers() {
        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("teacher", "maintainer"), List.of("maintainer"))), includes(),
                coveringWorkflow(), profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("tech-a");
        });
    }

    @Test
    void reportsProvidedCapabilityTheProfileDoesNotList() {
        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("maintainer"), List.of("maintainer"))), includes(),
                coveringWorkflow(), profile(List.of("alpha-service")));

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_PROFILE_CAPABILITY_MISMATCH);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.message()).contains("tech-capability");
        });
    }

    @Test
    void reportsWorkflowReferencingUnknownFeatureAsHardError() {
        GuidedDecisionOption unknown = new GuidedDecisionOption("enable-ghost", "Ghost", "Synthetic option.", List.of("ghost"), List.of(), null, null,
                List.of("Outcome."), List.of(), List.of(), List.of());
        GuidedWorkflow workflow = workflow(List.of(unknown));

        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("maintainer"), List.of("maintainer"))), includes(), workflow,
                profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_GENERATED_WORKFLOW_INVALID);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("ghost");
        });
        assertThat(result.artifactValidation().workflowIntegrityValid()).isFalse();
        assertThat(result.artifactValidation().snapshotEligible()).isFalse();
    }

    @Test
    void reportsCoverageGapAsFindingsStatusWithoutFailing() {
        GuidedDecisionOption partial = new GuidedDecisionOption("enable-nothing", "Nothing", "Synthetic option.", List.of(), List.of(), null, null,
                List.of("Outcome."), List.of(), List.of(), List.of());
        GuidedWorkflow workflow = workflow(List.of(partial));

        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("maintainer"), List.of("maintainer"))), includes(), workflow,
                profile(List.of("alpha-service", "tech-capability")));

        assertThat(result.guidedValidation().status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_FINDINGS);
        assertThat(result.guidedValidation().findings()).extracting(GuidedWorkflowFinding::code).contains(GuidedWorkflowFinding.CODE_COVERAGE_GAP);
        assertThat(result.guidedValidation().codeCounts()).containsKey(GuidedWorkflowFinding.CODE_COVERAGE_GAP);
        assertThat(result.items()).anySatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS));
        assertThat(result.artifactValidation().snapshotEligible()).isTrue();
        // Technical features never count as coverage gaps.
        assertThat(result.guidedValidation().findings()).extracting(GuidedWorkflowFinding::subject).doesNotContain("tech-a");
    }

    private FeatureModel model(FeatureNode technicalFeature) {
        FeatureNode root = new FeatureNode("root", "Root", "root", false, null, "not_applicable", null);
        FeatureNode group = new FeatureNode("alpha-group", "Alpha Group", "group", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha", "Alpha", "module", true, null, "enabled", null, "functional", List.of("teacher", "maintainer"),
                List.of("teacher", "maintainer"), List.of("alpha-service"), null, null);
        List<FeatureRelation> relations = List.of(new FeatureRelation("root", "alpha-group", "group", "and", 1),
                new FeatureRelation("alpha-group", "alpha", "optional", null, 1), new FeatureRelation("root", "tech-a", "optional", null, 2));
        return new FeatureModel(new ModelMetadata("generated-test-model", "Generated Test Model", "0.0.1"), List.of(root, group, alpha, technicalFeature),
                relations, List.of());
    }

    private FeatureNode technicalFeature(List<String> visibleTo, List<String> configurableBy) {
        return new FeatureNode("tech-a", "Tech A", "feature", true, null, "enabled", null, "technical", visibleTo, configurableBy, List.of(), null, null);
    }

    private List<ResolvedFeatureScope> includes() {
        return List.of(
                new ResolvedFeatureScope("module:alpha", "alpha", "alpha-group", null, "module", "optional", null, null, 1, List.of("alpha-service"), List.of(),
                        List.of(), null, null, null, "manifest"),
                new ResolvedFeatureScope("infra:tech-a", "tech-a", null, "root", "feature", "optional", "technical", "enabled", 2, List.of(),
                        List.of("tech-capability"), List.of(), "Tech A", null, null, "manifest"));
    }

    private GuidedWorkflow coveringWorkflow() {
        GuidedDecisionOption option = new GuidedDecisionOption("enable-alpha", "Alpha", "Synthetic option.", List.of("alpha"), List.of(), null, null,
                List.of("Outcome."), List.of(), List.of(), List.of());
        return workflow(List.of(option));
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
