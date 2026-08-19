package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformanceException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ModelAssemblyOutcome;
import de.tum.cit.aet.artemis.featuremodel.extraction.report.ExtractionReportAssembler;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code assembleFeatureModel} command: applies the manifest to an existing scan and assembles the generated
 * feature model, the regenerated config key catalog, and the standalone manifest-conformance verdict. It derives its
 * run identity from the verified checkout's HEAD but consumes the scan artifacts through the artifact store and never
 * rescans Artemis sources, and it fails without a model when the manifest leaves any discovered candidate or relation
 * undecided.
 */
public class ModelStageService {

    private final ObjectMapper objectMapper;

    private final ExtractionInputLoader inputLoader;

    private final ExtractionArtifactStore artifactStore;

    /**
     * Creates the model assembly command.
     *
     * @param objectMapper Jackson mapper shared with the assemblers and the artifact store.
     */
    public ModelStageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.inputLoader = new ExtractionInputLoader(objectMapper);
        this.artifactStore = new ExtractionArtifactStore(objectMapper);
    }

    /**
     * Result of one model assembly command.
     *
     * @param artemisCommit commit the model was assembled from.
     * @param modelDirectory directory the model artifacts were written to.
     * @param curationCounts manifest classification counts per state.
     * @param featureCount number of features in the generated model.
     * @param relationCount number of relations in the generated model.
     * @param constraintCount number of constraints in the generated model.
     * @param catalogKeyCount number of keys in the regenerated catalog.
     * @param modelIntegrityValid whether the generated model passed structural integrity validation.
     */
    public record Summary(String artemisCommit, Path modelDirectory, Map<String, Integer> curationCounts, int featureCount, int relationCount,
            int constraintCount, int catalogKeyCount, boolean modelIntegrityValid) {
    }

    /**
     * Runs one model assembly.
     *
     * @param inputs resolved command inputs.
     * @param sourceFactory creates the source repository over the configured checkout for revision derivation.
     * @return summary of the written model artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     * @throws ManifestConformanceException if the manifest leaves a candidate or relation undecided; diagnostics are
     *             written before the failure and no model is assembled.
     */
    public Summary run(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) throws IOException {
        ArtemisSourceRepository source = inputLoader.verifiedSource(inputs, sourceFactory);
        ExtractionRunContext context = inputLoader.runContext(inputs, source);
        FeatureScopeManifest manifest = context.manifest();
        artifactStore.invalidateFrom(context.layout(), ExtractionStage.MODEL);
        ExtractionArtifactStore.LoadedScan scan;
        ModelAssemblyOutcome outcome;
        try {
            scan = artifactStore.readScan(context.layout(), context.artemisCommit());
            outcome = new ModelAssemblyService(objectMapper).assemble(manifest, scan.outcome(), inputLoader.deploymentProfile(inputs),
                    context.artemisCommit());
        }
        catch (IOException | RuntimeException failure) {
            new ControlledFailureReportWriter(artifactStore).write(context, failure);
            throw failure;
        }
        artifactStore.writeModel(context.layout(), outcome, scan.result().payloadDigest(), context.manifestDigest(), context.artemisCommit());
        writeFailureReport(context, scan, outcome);
        failIfNotConformant(outcome);

        return new Summary(context.artemisCommit(), context.layout().modelDirectory(), outcome.curation().stateCounts(),
                outcome.generatedModel().features().size(), outcome.generatedModel().relations().size(), outcome.generatedModel().constraints().size(),
                outcome.generatedCatalog().keys().size(), outcome.modelIntegrityValid());
    }

    private void writeFailureReport(ExtractionRunContext context, ExtractionArtifactStore.LoadedScan scan, ModelAssemblyOutcome outcome)
            throws IOException {
        if (outcome.conformance().conformant() && outcome.generatedOutputConformant()) {
            return;
        }
        List<ReportItem> items = new ArrayList<>(scan.outcome().items());
        items.addAll(outcome.items());
        var report = new ExtractionReportAssembler().assemble(context.artemisCommit(), context.manifestDigest(), outcome.curation(), items, false);
        artifactStore.writeReport(context.layout(), report);
    }

    /**
     * Fails the command after the diagnostics of an incomplete curation have been written.
     *
     * @param outcome model assembly outcome containing both source and generated-output conformance.
     * @throws ManifestConformanceException if the manifest does not describe the scanned source completely.
     */
    private void failIfNotConformant(ModelAssemblyOutcome outcome) {
        ManifestConformance conformance = outcome.conformance();
        if (!conformance.conformant()) {
            throw new ManifestConformanceException("The manifest does not describe the scanned Artemis commit completely: " + conformance.describeFindings()
                    + ". Diagnostics were written, but no feature model was assembled.");
        }
        if (!outcome.generatedOutputConformant()) {
            throw new ManifestConformanceException("The generated model differs from the resolved manifest semantics. Diagnostics were written, and no "
                    + "downstream artifact may consume this model.");
        }
    }
}
