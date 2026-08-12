package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the repository-relative inputs every extraction stage shares. One verified checkout supplies the derived
 * source revision, and one manifest byte read produces the parsed manifest, digest, and revision-scoped artifact
 * layout in an {@link ExtractionRunContext}; other inputs remain independently loaded files because their stages
 * consume them at different boundaries.
 */
class ExtractionInputLoader {

    private final ObjectMapper objectMapper;

    private final ManifestBytesReader manifestBytesReader;

    /** Reads a manifest payload, injectable so the one-read boundary can be verified without filesystem races. */
    @FunctionalInterface
    interface ManifestBytesReader {

        /**
         * Reads the exact bytes at a manifest path.
         *
         * @param path configured manifest path.
         * @return manifest bytes.
         * @throws IOException if the manifest cannot be read.
         */
        byte[] read(Path path) throws IOException;
    }

    /**
     * Creates the input loader.
     *
     * @param objectMapper Jackson mapper used to parse the JSON inputs.
     */
    ExtractionInputLoader(ObjectMapper objectMapper) {
        this(objectMapper, Files::readAllBytes);
    }

    /**
     * Creates the input loader with an explicit manifest byte source.
     *
     * @param objectMapper Jackson mapper used to parse JSON inputs.
     * @param manifestBytesReader exact manifest-byte reader.
     */
    ExtractionInputLoader(ObjectMapper objectMapper, ManifestBytesReader manifestBytesReader) {
        this.objectMapper = objectMapper;
        this.manifestBytesReader = manifestBytesReader;
    }

    /**
     * Resolves the configured checkout and verifies that a clean, attributable source revision can be derived from
     * it, comparing against the externally expected revision when the inputs carry one.
     *
     * @param inputs resolved command inputs.
     * @param sourceFactory creates the source repository over the configured checkout.
     * @return verified source repository whose derived revision identifies this run.
     * @throws IllegalStateException if no Artemis checkout is configured.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException if no revision can be
     *             derived, the checkout is dirty, or the derived revision differs from the expected one.
     */
    ArtemisSourceRepository verifiedSource(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) {
        ArtemisSourceRepository source = sourceFactory.apply(inputs.requireArtemisCheckout());
        new ArtemisSourcePreflight().verify(source, inputs.expectedArtemisSha());
        return source;
    }

    /**
     * Loads the scope manifest once and binds every derived command value to those exact bytes and the verified
     * checkout's derived revision.
     *
     * @param inputs resolved command inputs.
     * @param source verified source repository supplying the derived revision.
     * @return per-command extraction context.
     * @throws IOException if the manifest cannot be read.
     */
    ExtractionRunContext runContext(FeatureExtractionInputs inputs, ArtemisSourceRepository source) throws IOException {
        byte[] manifestBytes = manifestBytesReader.read(inputs.manifestFile());
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(new ByteArrayInputStream(manifestBytes), inputs.manifestFile().toString());
        String artemisCommit = source.commit();
        return new ExtractionRunContext(manifestBytes, manifest, Sha256Digest.of(manifestBytes), artemisCommit,
                ExtractionArtifactLayout.forCommit(inputs.outputRoot(), artemisCommit));
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
