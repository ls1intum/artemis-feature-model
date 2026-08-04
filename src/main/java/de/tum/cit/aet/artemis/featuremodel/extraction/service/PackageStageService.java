package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code packageFeatureModelSnapshot} command: consolidates the diagnostics of every stage into the extraction
 * report and publishes the importable snapshot. It is the only stage that decides publication, and it publishes only
 * when the model and the workflow both passed their hard validations.
 */
public class PackageStageService {

    private final ObjectMapper objectMapper;

    private final ExtractionInputLoader inputLoader;

    private final ExtractionArtifactStore artifactStore;

    private final SnapshotPublisher snapshotPublisher;

    private final String featureModelRepositoryCommit;

    /**
     * Creates the snapshot packaging command.
     *
     * @param objectMapper Jackson mapper shared with the artifact store and the snapshot publisher.
     */
    public PackageStageService(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    /**
     * Creates the command with a fixed repository commit for deterministic fixture characterization.
     *
     * @param objectMapper Jackson mapper shared with artifact services.
     * @param featureModelRepositoryCommit fixed repository commit, or null to resolve the current Git commit.
     */
    PackageStageService(ObjectMapper objectMapper, String featureModelRepositoryCommit) {
        this.objectMapper = objectMapper;
        this.inputLoader = new ExtractionInputLoader(objectMapper);
        this.artifactStore = new ExtractionArtifactStore(objectMapper);
        this.snapshotPublisher = new SnapshotPublisher(objectMapper);
        this.featureModelRepositoryCommit = featureModelRepositoryCommit;
    }

    /**
     * Result of one packaging command.
     *
     * @param reportDirectory directory the consolidated report was written to.
     * @param snapshotDirectory published snapshot directory, or null when nothing was published.
     * @param severityCounts report item counts per severity.
     * @param codeCounts report item counts per diagnostic code.
     */
    public record Summary(Path reportDirectory, Path snapshotDirectory, Map<String, Integer> severityCounts, Map<String, Integer> codeCounts) {
    }

    /**
     * Runs one packaging command.
     *
     * @param inputs resolved command inputs.
     * @return summary of the consolidated report and the publication decision.
     * @throws IOException if an artifact cannot be read or written.
     * @throws IllegalStateException if the run is ineligible; diagnostics are written before the failure.
     */
    public Summary run(FeatureExtractionInputs inputs) throws IOException {
        ExtractionRunContext context = inputLoader.runContext(inputs);
        artifactStore.invalidateFrom(context.layout(), ExtractionStage.PACKAGE);
        ExtractionArtifactStore.LoadedScan scan;
        ExtractionArtifactStore.LoadedModel model;
        ExtractionArtifactStore.LoadedWorkflow workflow;
        try {
            scan = artifactStore.readScan(context.layout(), context.artemisCommit());
            model = artifactStore.readModel(context.layout(), context.artemisCommit(), scan.result().payloadDigest(), context.manifestDigest());
            workflow = artifactStore.readWorkflow(context.layout(), context.artemisCommit(), model.result().generatedModelDigest(),
                    Sha256Digest.of(inputs.authoredWorkflowFile()));
        }
        catch (IOException | RuntimeException failure) {
            new ControlledFailureReportWriter(artifactStore).write(context, failure);
            throw failure;
        }

        List<ReportItem> stageItems = new ArrayList<>(scan.outcome().items());
        stageItems.addAll(model.items());
        stageItems.addAll(workflow.items());
        boolean eligible = model.result().deliveryEligible() && workflow.result().deliveryEligible();
        ExtractionReport report = new ExtractionReportAssembler().assemble(context.artemisCommit(), context.manifestDigest(), model.result().curation(),
                stageItems, eligible);
        artifactStore.writeReport(context.layout(), report);
        boolean published = snapshotPublisher.publish(context.layout(), model.generatedModel(), workflow.preparedWorkflow(),
                artifactStore.readGeneratedCatalog(context.layout()), report, context.artemisCommit(), context.manifestDigest(),
                repositoryCommit(), Sha256Digest.of(inputs.deploymentProfileFile()), context.manifest().artemisImageDigest(),
                eligible);
        if (published) {
            try {
                new FeatureModelSnapshotValidator(objectMapper).validate(context.layout().snapshotDirectory());
            }
            catch (IOException | RuntimeException failure) {
                snapshotPublisher.invalidate(context.layout());
                throw failure;
            }
        }
        Summary summary = new Summary(context.layout().reportDirectory(), published ? context.layout().snapshotDirectory() : null, report.severityCounts(),
                report.codeCounts());
        failIfIneligible(eligible);
        return summary;
    }

    private String repositoryCommit() throws IOException {
        return featureModelRepositoryCommit == null ? new FeatureModelRepositoryCommitResolver().resolve() : featureModelRepositoryCommit;
    }

    /**
     * Fails the command after the diagnostics of an ineligible run have been written.
     *
     * @param eligible whether every gate that guards publication passed.
     * @throws IllegalStateException if the run is ineligible.
     */
    private void failIfIneligible(boolean eligible) {
        if (!eligible) {
            throw new IllegalStateException("A generated model, catalog, profile, or workflow delivery gate failed. Diagnostics were written, but no "
                    + "snapshot was published.");
        }
    }
}
