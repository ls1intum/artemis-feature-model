package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ExtractionOutputWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureManifestLoader;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import tools.jackson.databind.ObjectMapper;

/**
 * Command line entry point of the {@code extractFeatureModel} Gradle task. Runs the extraction, manifest curation,
 * and generated-model assembly pipeline against a local Artemis checkout without a Spring context and writes all
 * outputs into the directories of the run layout under the configured output root.
 */
public final class FeatureExtractionRunner {

    private static final Set<String> SUPPORTED_OPTIONS = Set.of(FeatureExtractionInputs.OPTION_ARTEMIS_PATH, FeatureExtractionInputs.OPTION_MANIFEST,
            FeatureExtractionInputs.OPTION_AUTHORED_WORKFLOW, FeatureExtractionInputs.OPTION_DEPLOYMENT_PROFILE, FeatureExtractionInputs.OPTION_CURATED_MODEL,
            FeatureExtractionInputs.OPTION_BOOTSTRAP_CATALOG, FeatureExtractionInputs.OPTION_OUTPUT_ROOT);

    private FeatureExtractionRunner() {
    }

    /**
     * Runs one extraction scan.
     *
     * @param args named {@code --option=value} arguments; see {@link FeatureExtractionInputs}.
     * @throws Exception if the scan cannot run at all; individual extractor failures only produce report items.
     */
    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureExtractionInputs inputs = FeatureExtractionInputs.resolve(ExtractionCommandOptions.parse(args, SUPPORTED_OPTIONS), System::getenv);
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(inputs.requireArtemisCheckout());
        FeatureModel curatedModel = readJson(objectMapper, inputs.curatedModelFile(), FeatureModel.class);
        ArtemisConfigKeyCatalog catalog = readJson(objectMapper, inputs.bootstrapCatalogFile(), ArtemisConfigKeyCatalog.class);
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(inputs.manifestFile());
        GuidedWorkflow authoredWorkflow = readJson(objectMapper, inputs.authoredWorkflowFile(), GuidedWorkflow.class);
        DeploymentProfile bundledProfile = readJson(objectMapper, inputs.deploymentProfileFile(), DeploymentProfile.class);

        String scanStartedAt = Instant.now().toString();
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, curatedModel, catalog, manifest, authoredWorkflow,
                bundledProfile);
        String scanFinishedAt = Instant.now().toString();

        ScanMetadata metadata = new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                scanStartedAt, scanFinishedAt, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.report().items().size());
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), source.commit());
        ExtractionOutputWriter writer = new ExtractionOutputWriter(objectMapper);
        writer.writeAll(layout, metadata, outcome);
        boolean snapshotPublished = writer.writeSnapshot(layout, outcome, Files.readAllBytes(inputs.authoredWorkflowFile()), source.root().toString(),
                source.commit());

        printSummary(outcome, layout, snapshotPublished);
        failIfSnapshotIneligible(outcome);
    }

    /**
     * Reads and parses a JSON input file.
     *
     * @param <T> payload type.
     * @param objectMapper Jackson mapper.
     * @param file input file.
     * @param type payload class.
     * @return parsed payload.
     * @throws IOException if the file cannot be read.
     */
    private static <T> T readJson(ObjectMapper objectMapper, Path file, Class<T> type) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return objectMapper.readValue(inputStream, type);
        }
    }

    /**
     * Prints a human-readable scan summary to standard output.
     *
     * @param outcome extraction outcome.
     * @param layout output layout the outputs were written to.
     * @param snapshotPublished whether an importable snapshot was published.
     */
    private static void printSummary(FeatureExtractionService.Outcome outcome, ExtractionArtifactLayout layout, boolean snapshotPublished) {
        System.out.println("Feature extraction finished.");
        System.out.println("  Candidates: " + outcome.candidates().size());
        System.out.println("  Evidence items: " + outcome.evidence().size());
        System.out.println("  Relation candidates: " + outcome.relationCandidates().size());
        System.out.println("  Report items: " + outcome.report().items().size() + " " + describeCounts(outcome.report().severityCounts()));
        System.out.println("  Curation: " + outcome.report().curation().stateCounts());
        outcome.report().codeCounts().forEach((code, count) -> System.out.println("    " + code + ": " + count));
        if (outcome.generatedModel() != null) {
            System.out.println("  Generated model: " + outcome.generatedModel().features().size() + " features, " + outcome.generatedModel().relations().size()
                    + " relations, " + outcome.generatedModel().constraints().size() + " constraints (version " + outcome.generatedModel().model().version() + ")");
            System.out.println("  Generated catalog: " + outcome.generatedCatalog().keys().size() + " keys");
            System.out.println("  Model diff: " + outcome.modelDiff().classificationCounts());
            System.out.println("  Guided workflow validation: " + outcome.guidedWorkflowValidation().status());
            String snapshotStatus = snapshotPublished ? layout.snapshotDirectory().toString() : "not published";
            System.out.println("  Importable snapshot: " + snapshotStatus);
        }
        System.out.println("  Output: " + layout.root());
    }

    /**
     * Makes a hard generated-artifact validation failure fail the command after diagnostic outputs have been written.
     *
     * @param outcome extraction outcome carrying the hard validation state.
     * @throws IllegalStateException if generation ran but the snapshot is ineligible.
     */
    static void failIfSnapshotIneligible(FeatureExtractionService.Outcome outcome) {
        if (outcome.generatedModel() != null && (outcome.artifactValidation() == null || !outcome.artifactValidation().snapshotEligible())) {
            throw new IllegalStateException("Generated model or workflow failed hard integrity validation. Diagnostics were written, but no importable "
                    + "snapshot was published.");
        }
    }

    /**
     * Formats the severity counts of the report.
     *
     * @param severityCounts item counts per severity.
     * @return formatted counts.
     */
    private static String describeCounts(Map<String, Integer> severityCounts) {
        return severityCounts.toString();
    }
}
