package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ExtractionJsonWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.FeatureManifestLoader;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.GuidedWorkflowScaffoldService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Command line entry point of the {@code syncGuidedWorkflowScaffold} Gradle task. Deliberately separate from the
 * automatic drift scan because it writes to a curated resource: the maintainer invokes it explicitly, reviews the
 * produced diff, and authors the prose of any generated stubs afterwards. A run without structural changes leaves the
 * authored file untouched.
 */
public final class GuidedWorkflowScaffoldRunner {

    private GuidedWorkflowScaffoldRunner() {
    }

    /**
     * Runs one scaffold sync.
     *
     * @param args first argument: authored guided workflow path; second argument: scope manifest path; third
     *            argument: sync report output path.
     * @throws Exception if the workflow or manifest cannot be read at all.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: GuidedWorkflowScaffoldRunner <workflowPath> <manifestPath> <reportPath>");
            System.exit(1);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        Path workflowPath = Path.of(args[0]);
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(Path.of(args[1]));
        ObjectNode workflow = (ObjectNode) objectMapper.readTree(Files.readString(workflowPath));

        GuidedWorkflowScaffoldService scaffoldService = new GuidedWorkflowScaffoldService(objectMapper);
        GuidedWorkflowScaffoldService.Result result = scaffoldService.sync(workflow, manifest);

        if (result.changed()) {
            Files.write(workflowPath, scaffoldService.writeWorkflow(result.workflow()).getBytes(StandardCharsets.UTF_8));
        }
        writeReport(objectMapper, Path.of(args[2]), result.report());
        printSummary(result, workflowPath, Path.of(args[2]));
    }

    /**
     * Writes the sync report as deterministic pretty-printed JSON.
     *
     * @param objectMapper Jackson mapper.
     * @param reportPath report output path.
     * @param report sync report.
     * @throws Exception if the report cannot be written.
     */
    private static void writeReport(ObjectMapper objectMapper, Path reportPath, GuidedWorkflowScaffoldService.ScaffoldReport report) throws Exception {
        Files.createDirectories(reportPath.getParent());
        new ExtractionJsonWriter(objectMapper).write(reportPath, report);
    }

    /**
     * Prints a human-readable sync summary.
     *
     * @param result sync result.
     * @param workflowPath authored workflow path.
     * @param reportPath report output path.
     */
    private static void printSummary(GuidedWorkflowScaffoldService.Result result, Path workflowPath, Path reportPath) {
        System.out.println("Guided workflow scaffold sync finished: " + result.report().status());
        if (result.changed()) {
            System.out.println("  Updated: " + workflowPath);
            System.out.println("  Added options: " + result.report().addedOptionIds());
            if (!result.report().addedDecisionIds().isEmpty()) {
                System.out.println("  Added scaffold decisions needing placement review: " + result.report().addedDecisionIds());
            }
            if (!result.report().addedReviewGroupNodeIds().isEmpty()) {
                System.out.println("  Added review groups: " + result.report().addedReviewGroupNodeIds());
            }
            if (!result.report().renamedIds().isEmpty()) {
                System.out.println("  Renamed ids: " + result.report().renamedIds());
            }
            System.out.println("  The stubs carry TODO prose; author the teacher-facing text before committing.");
        }
        if (!result.report().orphanReferences().isEmpty()) {
            System.out.println("  Orphan feature references kept for review: " + result.report().orphanReferences());
        }
        System.out.println("  Report: " + reportPath);
    }
}
