package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ControlledFailureReportWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionArtifactStore;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionInputLoader;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionRunContext;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code extractFeatureCandidates} command: reads the verified Artemis checkout once and writes the raw source
 * discovery artifacts. It is the only stage that reads Artemis sources, so every later command works from these files.
 *
 * <p>
 * The command derives its run identity from the checkout HEAD before it touches any artifact: a failure to establish
 * that identity — missing checkout configuration, no derivable revision, a dirty working tree, or a mismatch with the
 * externally expected revision — stops the run before anything is invalidated, because no run directory can be
 * attributed to it. Once the identity is established, the command invalidates its own output and every downstream
 * directory of that revision before scanning, so a scan failure leaves no artifact behind that a later command could
 * mistake for this run's output.
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
     * @param sourceFactory creates the source repository over the configured checkout.
     * @return summary of the written scan artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     * @throws IllegalStateException if no Artemis checkout is configured; the run has no identity, so nothing is
     *             invalidated.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException if no revision can be
     *             derived, the checkout is dirty, or the derived revision differs from the expected one; the run has
     *             no attributable identity, so nothing is invalidated.
     */
    public Summary run(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) throws IOException {
        ArtemisSourceRepository source = inputLoader.verifiedSource(inputs, sourceFactory);
        ExtractionRunContext context = inputLoader.runContext(inputs, source);
        artifactStore.invalidateFrom(context.layout(), ExtractionStage.SCAN);
        try {
            String scanStartedAt = Instant.now().toString();
            ExtractedSourceFacts outcome = new FeatureExtractionService(objectMapper).scan(source);
            String scanFinishedAt = Instant.now().toString();

            ScanMetadata metadata = new ScanMetadata(ScanResult.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                    scanStartedAt, scanFinishedAt, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                    outcome.items().size());
            artifactStore.writeScan(context.layout(), metadata, outcome);
            return new Summary(source.commit(), context.layout().scanDirectory(), outcome.candidates().size(), outcome.evidence().size(),
                    outcome.relationCandidates().size(), outcome.items().size());
        }
        catch (IOException | RuntimeException failure) {
            new ControlledFailureReportWriter(artifactStore).write(context, failure);
            throw failure;
        }
    }
}
