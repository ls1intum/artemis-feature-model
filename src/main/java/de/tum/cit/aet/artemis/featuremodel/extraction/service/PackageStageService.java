package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code packageFeatureModelSnapshot} command: consolidates the diagnostics of every stage into the extraction
 * report and publishes the importable snapshot. It is the only stage that decides publication, and it publishes only
 * when the model and the workflow both passed their hard validations.
 */
public class PackageStageService {

    private final ExtractionInputLoader inputLoader;

    private final ExtractionArtifactStore artifactStore;

    private final SnapshotPublisher snapshotPublisher;

    /**
     * Creates the snapshot packaging command.
     *
     * @param objectMapper Jackson mapper shared with the artifact store and the snapshot publisher.
     */
    public PackageStageService(ObjectMapper objectMapper) {
        this.inputLoader = new ExtractionInputLoader(objectMapper);
        this.artifactStore = new ExtractionArtifactStore(objectMapper);
        this.snapshotPublisher = new SnapshotPublisher(objectMapper);
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
        FeatureScopeManifest manifest = inputLoader.manifest(inputs);
        String artemisCommit = manifest.artemisCommitSha();
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), artemisCommit);
        String manifestDigest = inputLoader.manifestDigest(inputs);
        artifactStore.invalidateFrom(layout, ExtractionStage.PACKAGE);
        ExtractionArtifactStore.LoadedScan scan = artifactStore.readScan(layout, artemisCommit);
        ExtractionArtifactStore.LoadedModel model = artifactStore.readModel(layout, artemisCommit, scan.result().payloadDigest(), manifestDigest);
        ExtractionArtifactStore.LoadedWorkflow workflow = artifactStore.readWorkflow(layout, artemisCommit, model.result().generatedModelDigest(),
                ExtractionArtifactStore.digestOf(inputs.authoredWorkflowFile()));

        List<ReportItem> stageItems = new ArrayList<>(scan.outcome().items());
        stageItems.addAll(model.items());
        stageItems.addAll(workflow.items());
        ExtractionReport report = new ExtractionReportAssembler().assemble(inputLoader.curatedModel(inputs), artemisCommit, model.result().curation(),
                stageItems);
        artifactStore.writeReport(layout, report);

        boolean eligible = model.result().modelIntegrityValid() && workflow.result().workflowIntegrityValid();
        boolean published = snapshotPublisher.publish(layout, model.generatedModel(), workflow.preparedWorkflow(), scan.metadata().artemisPath(), artemisCommit,
                manifest.artemisImageDigest(), eligible);
        Summary summary = new Summary(layout.reportDirectory(), published ? layout.snapshotDirectory() : null, report.severityCounts(), report.codeCounts());
        failIfIneligible(eligible);
        return summary;
    }

    /**
     * Fails the command after the diagnostics of an ineligible run have been written.
     *
     * @param eligible whether every gate that guards publication passed.
     * @throws IllegalStateException if the run is ineligible.
     */
    private void failIfIneligible(boolean eligible) {
        if (!eligible) {
            throw new IllegalStateException("Generated model or workflow failed hard integrity validation. Diagnostics were written, but no importable "
                    + "snapshot was published.");
        }
    }
}
