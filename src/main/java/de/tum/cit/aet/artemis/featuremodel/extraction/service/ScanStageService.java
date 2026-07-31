package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

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
     * @param source Artemis source repository to scan.
     * @return summary of the written scan artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     */
    public Summary run(FeatureExtractionInputs inputs, ArtemisSourceRepository source) throws IOException {
        FeatureScopeManifest manifest = inputLoader.manifest(inputs);
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), manifest.verifiedAgainstArtemisCommit());
        artifactStore.invalidateFrom(layout, ExtractionStage.SCAN);

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
