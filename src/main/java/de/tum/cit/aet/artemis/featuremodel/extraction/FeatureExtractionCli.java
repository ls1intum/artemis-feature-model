package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ManifestPreflightService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.WorkflowStageService;
import tools.jackson.databind.ObjectMapper;

/**
 * Single command line entry point of the extraction commands. It parses the named options, resolves the inputs,
 * dispatches to one stage service, prints its summary, and turns a failed stage into a non-zero exit code. All
 * behavior lives in the stage services; this class only wires them to the build.
 */
public final class FeatureExtractionCli {

    private static final String SCAN_COMMAND = "scan";

    private static final String MODEL_COMMAND = "model";

    private static final String WORKFLOW_COMMAND = "workflow";

    private static final String SNAPSHOT_COMMAND = "snapshot";

    private static final String PREFLIGHT_COMMAND = "preflight";

    private static final Set<String> SUPPORTED_OPTIONS = Set.of(FeatureExtractionInputs.OPTION_ARTEMIS_PATH, FeatureExtractionInputs.OPTION_MANIFEST,
            FeatureExtractionInputs.OPTION_AUTHORED_WORKFLOW, FeatureExtractionInputs.OPTION_DEPLOYMENT_PROFILE, FeatureExtractionInputs.OPTION_OUTPUT_ROOT);

    private FeatureExtractionCli() {
    }

    /**
     * Runs one extraction command.
     *
     * @param args subcommand followed by named {@code --option=value} arguments.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: FeatureExtractionCli <preflight|scan|model|workflow|snapshot> [--option=value ...]");
            System.exit(1);
        }
        try {
            run(args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        catch (Exception e) {
            System.err.println("Extraction command '" + args[0] + "' failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Dispatches one subcommand.
     *
     * @param command subcommand name.
     * @param arguments named command arguments.
     * @throws Exception if the command fails; the caller turns it into a non-zero exit code.
     */
    private static void run(String command, String[] arguments) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> options = ExtractionCommandOptions.parse(arguments, SUPPORTED_OPTIONS);
        FeatureExtractionInputs inputs = FeatureExtractionInputs.resolve(options, System::getenv);
        switch (command) {
            case SCAN_COMMAND -> printScanSummary(new ScanStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new));
            case MODEL_COMMAND -> printModelSummary(new ModelStageService(objectMapper).run(inputs));
            case WORKFLOW_COMMAND -> printWorkflowSummary(new WorkflowStageService(objectMapper).run(inputs));
            case SNAPSHOT_COMMAND -> printPackageSummary(new PackageStageService(objectMapper).run(inputs));
            case PREFLIGHT_COMMAND -> printPreflightSummary(new ManifestPreflightService(objectMapper).run(inputs));
            default -> throw new IllegalArgumentException("Unknown command '" + command + "'; expected preflight, scan, model, workflow, or snapshot.");
        }
    }

    /**
     * Prints the manifest preflight result as machine-readable key-value lines a build or workflow can consume.
     *
     * @param summary preflight result.
     */
    private static void printPreflightSummary(ManifestPreflightService.Summary summary) {
        System.out.println("manifestVersion=" + summary.manifestVersion());
        System.out.println("artemisCommitSha=" + summary.artemisCommitSha());
        System.out.println("manifestDigest=" + summary.manifestDigest());
        System.out.println("includeCount=" + summary.includeCount());
        System.out.println("excludeCount=" + summary.excludeCount());
    }

    /**
     * Prints the summary of a scan command.
     *
     * @param summary scan result.
     */
    private static void printScanSummary(ScanStageService.Summary summary) {
        System.out.println("Artemis scan finished.");
        System.out.println("  Artemis commit: " + summary.artemisCommit());
        System.out.println("  Candidates: " + summary.candidateCount());
        System.out.println("  Evidence items: " + summary.evidenceCount());
        System.out.println("  Relation candidates: " + summary.relationCandidateCount());
        System.out.println("  Scan diagnostics: " + summary.diagnosticCount());
        System.out.println("  Output: " + summary.scanDirectory());
    }

    /**
     * Prints the summary of a model assembly command.
     *
     * @param summary model assembly result.
     */
    private static void printModelSummary(ModelStageService.Summary summary) {
        System.out.println("Feature model assembly finished.");
        System.out.println("  Curation: " + summary.curationCounts());
        System.out.println("  Generated model: " + summary.featureCount() + " features, " + summary.relationCount() + " relations, " + summary.constraintCount()
                + " constraints");
        System.out.println("  Generated catalog: " + summary.catalogKeyCount() + " keys");
        System.out.println("  Model integrity valid: " + summary.modelIntegrityValid());
        System.out.println("  Output: " + summary.modelDirectory());
    }

    /**
     * Prints the summary of a workflow preparation command.
     *
     * @param summary workflow preparation result.
     */
    private static void printWorkflowSummary(WorkflowStageService.Summary summary) {
        System.out.println("Guided workflow preparation finished.");
        System.out.println("  Guided workflow validation: " + summary.validationStatus() + " " + summary.severityCounts());
        summary.codeCounts().forEach((code, count) -> System.out.println("    " + code + ": " + count));
        System.out.println("  Workflow integrity valid: " + summary.workflowIntegrityValid());
        System.out.println("  Delivery eligible: " + summary.deliveryEligible());
        System.out.println("  Output: " + summary.workflowDirectory());
    }

    /**
     * Prints the summary of a snapshot packaging command.
     *
     * @param summary packaging result.
     */
    private static void printPackageSummary(PackageStageService.Summary summary) {
        System.out.println("Feature model snapshot packaging finished.");
        System.out.println("  Report items: " + summary.severityCounts());
        summary.codeCounts().forEach((code, count) -> System.out.println("    " + code + ": " + count));
        System.out.println("  Importable snapshot: " + (summary.snapshotDirectory() == null ? "not published" : summary.snapshotDirectory()));
        System.out.println("  Output: " + summary.reportDirectory());
    }
}
