package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code extractFeatureCandidates} command: reads the pinned Artemis checkout once and writes the raw source
 * discovery artifacts. It is the only stage that opens Artemis, so every later command works from these files.
 *
 * <p>
 * The command invalidates its own output and every downstream directory before it resolves the checkout, so any way
 * this scan can fail — missing checkout configuration, a checkout at another commit, a dirty working tree, or a write
 * error — leaves no artifact of a previous run behind that a later command could mistake for this one.
 */
public class ScanStageService {

    private final ObjectMapper objectMapper;

    private final ExtractionInputLoader inputLoader;

    private final ExtractionArtifactStore artifactStore;

    /**
     * Creates the scan command.
     *
     * @param objectMapper Jackson mapper shared with the scans and the artifact store.
     */
    public ScanStageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.inputLoader = new ExtractionInputLoader(objectMapper);
        this.artifactStore = new ExtractionArtifactStore(objectMapper);
    }

    /**
     * Result of one scan command.
     *
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param scanDirectory directory the scan artifacts were written to.
     * @param candidateCount number of feature candidates written.
     * @param evidenceCount number of evidence items written.
     * @param relationCandidateCount number of relation candidates written.
     * @param diagnosticCount number of scan diagnostics written.
     */
    public record Summary(String artemisCommit, Path scanDirectory, int candidateCount, int evidenceCount, int relationCandidateCount, int diagnosticCount) {
    }

    /**
     * Runs one scan.
     *
     * @param inputs resolved command inputs.
     * @param sourceFactory creates the source repository over the configured checkout. The command resolves the
     *            checkout itself, so a missing checkout configuration fails inside the same invalidation boundary as
     *            every other scan failure.
     * @return summary of the written scan artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     * @throws IllegalStateException if no Artemis checkout is configured; no artifact of this run survives.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException if the checkout is not the
     *             pinned commit or is not clean; the scan does not run and no artifact of this run survives.
     */
    public Summary run(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) throws IOException {
        FeatureScopeManifest manifest = inputLoader.manifest(inputs);
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), manifest.artemisCommitSha());
        artifactStore.invalidateFrom(layout, ExtractionStage.SCAN);
        ArtemisSourceRepository source = sourceFactory.apply(inputs.requireArtemisCheckout());
        new ArtemisSourcePreflight().verify(source, manifest.artemisCommitSha());

        String scanStartedAt = Instant.now().toString();
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).scan(source, inputLoader.curatedModel(inputs),
                inputLoader.bootstrapCatalog(inputs));
        String scanFinishedAt = Instant.now().toString();

        ScanMetadata metadata = new ScanMetadata(ScanResult.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                scanStartedAt, scanFinishedAt, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.items().size());
        artifactStore.writeScan(layout, metadata, outcome);
        return new Summary(source.commit(), layout.scanDirectory(), outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.items().size());
    }
}
