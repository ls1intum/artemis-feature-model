package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.RenameEntry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Covers idempotence, stub generation, orphan flagging, and explicit id-rename rewriting of the scaffold sync. */
class GuidedWorkflowScaffoldServiceTest {

    private static final Path AUTHORED_WORKFLOW = Path.of("src/main/resources/feature-model/guided-workflow.json");

    private static final Path BUNDLED_MANIFEST = Path.of("src/main/resources/feature-model/extraction/artemis-feature-manifest.yml");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GuidedWorkflowScaffoldService service = new GuidedWorkflowScaffoldService(objectMapper);

    @Test
    void parseAndWriteRoundTripKeepsTheAuthoredFileByteIdentical() throws Exception {
        String authored = Files.readString(AUTHORED_WORKFLOW);

        String written = service.writeWorkflow((ObjectNode) objectMapper.readTree(authored));

        assertThat(written).isEqualTo(authored);
    }

    @Test
    void bundledWorkflowAndManifestProduceNoChanges() throws Exception {
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(BUNDLED_MANIFEST);
        String authored = Files.readString(AUTHORED_WORKFLOW);
        ObjectNode workflow = (ObjectNode) objectMapper.readTree(authored);

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.changed()).isFalse();
        assertThat(result.report().status()).isEqualTo("no-changes");
        assertThat(result.report().orphanReferences()).isEmpty();
        assertThat(service.writeWorkflow(result.workflow())).isEqualTo(authored);
    }

    @Test
    void stubsNewlyIncludedFeatureWithWiringAndTodoProse() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha", "content-group", null), include("newcomer", "content-group", "Newcomer Feature")));
        ObjectNode workflow = workflowCovering("alpha");

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.changed()).isTrue();
        assertThat(result.report().addedOptionIds()).containsExactly("enable-newcomer");
        ObjectNode stub = lastOption(result.workflow());
        assertThat(stub.path("id").asString()).isEqualTo("enable-newcomer");
        // Scaffolded options are always drafts; publication stays a deliberate maintainer edit.
        assertThat(stub.path("status").asString()).isEqualTo("draft");
        assertThat(stub.path("label").asString()).isEqualTo("Newcomer Feature");
        assertThat(stub.withArrayProperty("selects")).hasSize(1);
        assertThat(stub.withArrayProperty("selects").get(0).asString()).isEqualTo("newcomer");
        assertThat(stub.path("description").asString()).startsWith("TODO");
        assertThat(stub.withArrayProperty("enabledOutcome").get(0).asString()).startsWith("TODO");
        // The stub joins the decision already covering the group, and the review group is derived from the group node.
        assertThat(result.report().addedDecisionIds()).isEmpty();
        assertThat(result.report().addedReviewGroupNodeIds()).isEmpty();
    }

    @Test
    void appendsReviewGroupForNewGroupNode() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha", "content-group", null), include("newcomer", "new-group", null)),
                List.of(rootNode(), groupNode("content-group"), groupNode("new-group")));
        ObjectNode workflow = workflowCovering("alpha");

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.report().addedReviewGroupNodeIds()).containsExactly("new-group");
        ArrayNode reviewGroups = result.workflow().withArrayProperty("finalReviewGroups");
        assertThat(reviewGroups.get(reviewGroups.size() - 1).path("groupNodeId").asString()).isEqualTo("new-group");
        // No existing decision covers the new group, so a scaffold decision is appended for placement review.
        assertThat(result.report().addedDecisionIds()).containsExactly("new-group-scaffold");
    }

    @Test
    void neverTouchesExistingProseWhenStubbing() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha", "content-group", null), include("newcomer", "content-group", null)));
        ObjectNode workflow = workflowCovering("alpha");
        String alphaProseBefore = firstOption(workflow).path("enabledOutcome").get(0).asString();

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(firstOption(result.workflow()).path("enabledOutcome").get(0).asString()).isEqualTo(alphaProseBefore);
        assertThat(firstOption(result.workflow()).path("label").asString()).isEqualTo("Enable Alpha");
    }

    @Test
    void flagsOrphanReferenceWithoutDeleting() {
        // The workflow still references removed-feature although the manifest no longer includes it, and separately
        // covers everything eligible, so no rename pairing applies.
        FeatureScopeManifest manifest = manifest(List.of(include("alpha", "content-group", null)));
        ObjectNode workflow = workflowCovering("alpha");
        ObjectNode orphanOption = objectMapper.createObjectNode();
        orphanOption.put("id", "enable-removed");
        orphanOption.put("label", "Removed");
        orphanOption.put("description", "Authored prose that must survive.");
        orphanOption.withArrayProperty("selects").add("removed-feature");
        orphanOption.withArrayProperty("deselects");
        firstDecision(workflow).withArrayProperty("options").add(orphanOption);
        ObjectNode secondOrphan = objectMapper.createObjectNode();
        secondOrphan.put("id", "enable-second-removed");
        secondOrphan.withArrayProperty("selects").add("second-removed-feature");
        firstDecision(workflow).withArrayProperty("options").add(secondOrphan);

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.report().orphanReferences()).containsExactly("removed-feature", "second-removed-feature");
        assertThat(result.changed()).isFalse();
        assertThat(optionIds(firstDecision(result.workflow()))).contains("enable-removed", "enable-second-removed");
    }

    @Test
    void doesNotInferRenameFromOneRemovedAndOneNewFeature() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha-renamed", "content-group", null)));
        ObjectNode workflow = workflowCovering("alpha");

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.changed()).isTrue();
        assertThat(result.report().renamedIds()).isEmpty();
        assertThat(result.report().orphanReferences()).containsExactly("alpha");
        assertThat(result.report().addedOptionIds()).containsExactly("enable-alpha-renamed");
        assertThat(firstOption(result.workflow()).withArrayProperty("selects").get(0).asString()).isEqualTo("alpha");
    }

    @Test
    void appliesExplicitRenamesAcrossAllReferenceLocationsWithoutChangingProse() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha-renamed", "content-group", null)),
                List.of(rootNode(), groupNode("content-group")),
                List.of(new RenameEntry("alpha", "alpha-renamed", "The module kept its semantics."),
                        new RenameEntry("old-content-group", "content-group", "The group kept its semantics.")));
        ObjectNode workflow = workflowCovering("alpha");
        ObjectNode template = (ObjectNode) workflow.withArrayProperty("useCaseTemplates").get(0);
        template.withArrayProperty("deselectedFeatureIds").add("alpha");
        firstOption(workflow).withArrayProperty("deselects").add("alpha");
        ((ObjectNode) workflow.withArrayProperty("finalReviewGroups").get(0)).put("groupNodeId", "old-content-group");
        String workflowBefore = workflow.toString();

        GuidedWorkflowScaffoldService.Result result = service.sync(workflow, manifest);

        assertThat(result.report().renamedIds()).containsExactly("alpha->alpha-renamed", "old-content-group->content-group");
        assertThat(template.withArrayProperty("selectedFeatureIds")).extracting(node -> node.asString()).containsExactly("alpha-renamed");
        assertThat(template.withArrayProperty("deselectedFeatureIds")).extracting(node -> node.asString()).containsExactly("alpha-renamed");
        assertThat(firstOption(result.workflow()).withArrayProperty("selects")).extracting(node -> node.asString()).containsExactly("alpha-renamed");
        assertThat(firstOption(result.workflow()).withArrayProperty("deselects")).extracting(node -> node.asString()).containsExactly("alpha-renamed");
        assertThat(result.workflow().withArrayProperty("finalReviewGroups").get(0).path("groupNodeId").asString()).isEqualTo("content-group");
        assertThat(result.workflow().toString().replace("alpha-renamed", "alpha").replace("content-group", "old-content-group")).isEqualTo(workflowBefore);
    }

    @Test
    void rejectsExplicitRenameToAlreadyCoveredTarget() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha-renamed", "content-group", null)),
                List.of(rootNode(), groupNode("content-group")),
                List.of(new RenameEntry("alpha", "alpha-renamed", "The module kept its semantics.")));
        ObjectNode workflow = workflowCovering("alpha");
        firstDecision(workflow).withArrayProperty("options").addObject().put("id", "already-covered").withArrayProperty("selects").add("alpha-renamed");

        assertThatThrownBy(() -> service.sync(workflow, manifest)).isInstanceOf(FeatureManifestException.class)
                .hasMessageContaining("already-covered target 'alpha-renamed'");
    }

    @Test
    void secondRunAfterExplicitRenameIsIdempotent() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha-renamed", "content-group", null)),
                List.of(rootNode(), groupNode("content-group")),
                List.of(new RenameEntry("alpha", "alpha-renamed", "The module kept its semantics.")));
        ObjectNode workflow = workflowCovering("alpha");

        GuidedWorkflowScaffoldService.Result firstRun = service.sync(workflow, manifest);
        String afterFirstRun = service.writeWorkflow(firstRun.workflow());
        GuidedWorkflowScaffoldService.Result secondRun = service.sync(firstRun.workflow(), manifest);

        assertThat(firstRun.report().renamedIds()).containsExactly("alpha->alpha-renamed");
        assertThat(secondRun.changed()).isFalse();
        assertThat(service.writeWorkflow(secondRun.workflow())).isEqualTo(afterFirstRun);
    }

    @Test
    void secondRunProducesNoFurtherChanges() {
        FeatureScopeManifest manifest = manifest(List.of(include("alpha", "content-group", null), include("newcomer", "content-group", null)));
        ObjectNode workflow = workflowCovering("alpha");

        GuidedWorkflowScaffoldService.Result firstRun = service.sync(workflow, manifest);
        String afterFirstRun = service.writeWorkflow(firstRun.workflow());
        GuidedWorkflowScaffoldService.Result secondRun = service.sync(firstRun.workflow(), manifest);

        assertThat(firstRun.changed()).isTrue();
        assertThat(secondRun.changed()).isFalse();
        assertThat(service.writeWorkflow(secondRun.workflow())).isEqualTo(afterFirstRun);
    }

    private FeatureScopeManifest manifest(List<IncludeEntry> includes) {
        return manifest(includes, List.of(rootNode(), groupNode("content-group")));
    }

    private FeatureScopeManifest manifest(List<IncludeEntry> includes, List<ConceptualNode> conceptualNodes) {
        return manifest(includes, conceptualNodes, List.of());
    }

    private FeatureScopeManifest manifest(List<IncludeEntry> includes, List<ConceptualNode> conceptualNodes, List<RenameEntry> renames) {
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, includes, List.of(), conceptualNodes, List.of(), List.of(), renames);
    }

    private IncludeEntry include(String id, String group, String name) {
        return new IncludeEntry("module:" + id, id, group, null, null, null, null, null, null, List.of(), List.of(), List.of(), name, null, null, null);
    }

    private ConceptualNode rootNode() {
        return new ConceptualNode("root", null, "root", null, null, null, null, "Root", null);
    }

    private ConceptualNode groupNode(String id) {
        return new ConceptualNode(id, "root", "group", null, null, null, null, null, null);
    }

    private ObjectNode workflowCovering(String featureId) {
        ObjectNode workflow = objectMapper.createObjectNode();
        ObjectNode metadata = workflow.putObject("workflow");
        metadata.put("id", "test-workflow");
        metadata.put("name", "Test Workflow");
        metadata.put("version", "0.0.1");
        metadata.put("defaultTemplateId", "custom");
        ObjectNode template = workflow.withArrayProperty("useCaseTemplates").addObject();
        template.put("id", "custom");
        template.put("label", "Custom");
        template.put("description", "Synthetic template.");
        template.withArrayProperty("selectedFeatureIds").add(featureId);
        template.withArrayProperty("deselectedFeatureIds");
        template.withArrayProperty("recommendedStepIds");
        template.withArrayProperty("consequences");
        template.withArrayProperty("warnings");
        ObjectNode step = workflow.withArrayProperty("steps").addObject();
        step.put("id", "content");
        step.put("title", "Content");
        step.put("order", 1);
        step.put("description", "Synthetic step.");
        ObjectNode decision = step.withArrayProperty("decisions").addObject();
        decision.put("id", "content-decision");
        decision.put("question", "Which content?");
        decision.put("description", "Synthetic decision.");
        decision.put("selectionMode", "multiple");
        ObjectNode option = decision.withArrayProperty("options").addObject();
        option.put("id", "enable-" + featureId);
        option.put("label", "Enable Alpha");
        option.put("description", "Authored description.");
        option.withArrayProperty("selects").add(featureId);
        option.withArrayProperty("deselects");
        option.withArrayProperty("warnings");
        option.withArrayProperty("enabledOutcome").add("Authored outcome prose.");
        option.withArrayProperty("recommendedWhen").add("Authored recommendation.");
        option.withArrayProperty("thingsToKnow").add("Authored note.");
        ObjectNode reviewGroup = workflow.withArrayProperty("finalReviewGroups").addObject();
        reviewGroup.put("groupNodeId", "content-group");
        reviewGroup.put("title", "Content");
        reviewGroup.put("order", 1);
        return workflow;
    }

    private ObjectNode firstDecision(ObjectNode workflow) {
        return (ObjectNode) workflow.withArrayProperty("steps").get(0).withArrayProperty("decisions").get(0);
    }

    private ObjectNode firstOption(ObjectNode workflow) {
        return (ObjectNode) firstDecision(workflow).withArrayProperty("options").get(0);
    }

    private ObjectNode lastOption(ObjectNode workflow) {
        ArrayNode options = firstDecision(workflow).withArrayProperty("options");
        return (ObjectNode) options.get(options.size() - 1);
    }

    private List<String> optionIds(ObjectNode decision) {
        List<String> ids = new ArrayList<>();
        decision.withArrayProperty("options").forEach(option -> ids.add(option.path("id").asString()));
        return ids;
    }
}
