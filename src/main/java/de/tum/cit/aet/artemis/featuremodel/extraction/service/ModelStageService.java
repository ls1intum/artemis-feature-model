package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code assembleFeatureModel} command: applies the manifest to an existing scan and assembles the generated
 * feature model, the regenerated config key catalog, and the classified comparison against the curated model. It
 * consumes the scan artifacts through the artifact store and never reopens the Artemis checkout.
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
     * @param diffCounts classified difference counts against the curated model.
     * @param modelIntegrityValid whether the generated model passed structural integrity validation.
     */
    public record Summary(String artemisCommit, Path modelDirectory, Map<String, Integer> curationCounts, int featureCount, int relationCount,
            int constraintCount, int catalogKeyCount, Map<String, Integer> diffCounts, boolean modelIntegrityValid) {
    }

    /**
     * Runs one model assembly.
     *
     * @param inputs resolved command inputs.
     * @return summary of the written model artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     */
    public Summary run(FeatureExtractionInputs inputs) throws IOException {
        FeatureScopeManifest manifest = inputLoader.manifest(inputs);
        String artemisCommit = manifest.artemisCommitSha();
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), artemisCommit);
        ExtractionArtifactStore.LoadedScan scan = artifactStore.readScan(layout, artemisCommit);
        artifactStore.invalidateFrom(layout, ExtractionStage.MODEL);

        ModelAssemblyService.Outcome outcome = new ModelAssemblyService(objectMapper).assemble(manifest, scan.outcome(), inputLoader.curatedModel(inputs),
                inputLoader.bootstrapCatalog(inputs), inputLoader.deploymentProfile(inputs), artemisCommit);
        artifactStore.writeModel(layout, outcome, scan.result().payloadDigest(), inputLoader.manifestDigest(inputs), artemisCommit);

        return new Summary(artemisCommit, layout.modelDirectory(), outcome.curation().stateCounts(), outcome.generatedModel().features().size(),
                outcome.generatedModel().relations().size(), outcome.generatedModel().constraints().size(), outcome.generatedCatalog().keys().size(),
                outcome.modelDiff().classificationCounts(), outcome.modelIntegrityValid());
    }
}
