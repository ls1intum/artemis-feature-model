package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the repository-relative inputs every extraction stage shares. The stages read their inputs from files rather
 * than from the classpath so that one configuration boundary decides which manifest, workflow, profile, model, and
 * catalog a run consumes.
 */
class ExtractionInputLoader {

    private final ObjectMapper objectMapper;

    /**
     * Creates the input loader.
     *
     * @param objectMapper Jackson mapper used to parse the JSON inputs.
     */
    ExtractionInputLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and validates the scope manifest.
     *
     * @param inputs resolved command inputs.
     * @return parsed manifest.
     * @throws IOException if the manifest cannot be read.
     */
    FeatureScopeManifest manifest(FeatureExtractionInputs inputs) throws IOException {
        return new FeatureManifestLoader().load(inputs.manifestFile());
    }

    /**
     * Computes the digest that identifies the manifest a stage consumed.
     *
     * @param inputs resolved command inputs.
     * @return manifest digest.
     * @throws IOException if the manifest cannot be read.
     */
    String manifestDigest(FeatureExtractionInputs inputs) throws IOException {
        return ExtractionArtifactStore.digestOf(inputs.manifestFile());
    }

    /**
     * Loads the curated feature model used by the drift and diff comparison.
     *
     * @param inputs resolved command inputs.
     * @return curated model.
     * @throws IOException if the model cannot be read.
     */
    FeatureModel curatedModel(FeatureExtractionInputs inputs) throws IOException {
        return readJson(inputs.curatedModelFile(), FeatureModel.class);
    }

    /**
     * Loads the bootstrap config key catalog used by the drift and diff comparison.
     *
     * @param inputs resolved command inputs.
     * @return curated catalog.
     * @throws IOException if the catalog cannot be read.
     */
    ArtemisConfigKeyCatalog bootstrapCatalog(FeatureExtractionInputs inputs) throws IOException {
        return readJson(inputs.bootstrapCatalogFile(), ArtemisConfigKeyCatalog.class);
    }

    /**
     * Loads the bundled deployment profile used by the capability cross-checks.
     *
     * @param inputs resolved command inputs.
     * @return deployment profile.
     * @throws IOException if the profile cannot be read.
     */
    DeploymentProfile deploymentProfile(FeatureExtractionInputs inputs) throws IOException {
        return readJson(inputs.deploymentProfileFile(), DeploymentProfile.class);
    }

    /**
     * Reads the raw bytes of the authored guided workflow, which the workflow stage copies verbatim.
     *
     * @param inputs resolved command inputs.
     * @return authored workflow bytes.
     * @throws IOException if the workflow cannot be read.
     */
    byte[] authoredWorkflowBytes(FeatureExtractionInputs inputs) throws IOException {
        return Files.readAllBytes(inputs.authoredWorkflowFile());
    }

    /**
     * Reads and parses a JSON input file.
     *
     * @param <T> payload type.
     * @param file input file.
     * @param type payload class.
     * @return parsed payload.
     * @throws IOException if the file cannot be read.
     */
    private <T> T readJson(Path file, Class<T> type) throws IOException {
        return objectMapper.readValue(Files.readAllBytes(file), type);
    }
}
