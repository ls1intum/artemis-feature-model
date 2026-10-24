package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Resolves the Artemis runtime image from the active snapshot or the classpath runtime properties. A configured
 * {@code sha256:} digest pins the image; the configured value {@code latest} pins none, so a package runs the
 * per-commit image of the model's source commit.
 */
@Component
public class ArtemisRuntimeSourceResolver {

    private static final String IMAGE_REPOSITORY = "ghcr.io/ls1intum/artemis";

    /** Configured digest value that pins no digest, so packages run the per-commit image of the model's source commit. */
    private static final String NO_PINNED_DIGEST = "latest";

    private final RuntimeFeatureModelBundle runtimeBundle;

    private final ArtemisRuntimeProperties runtimeProperties;

    /**
     * Creates the resolver.
     *
     * @param runtimeBundle validated runtime bundle used to inspect snapshot metadata.
     * @param runtimeProperties classpath fallback runtime properties.
     */
    public ArtemisRuntimeSourceResolver(RuntimeFeatureModelBundle runtimeBundle, ArtemisRuntimeProperties runtimeProperties) {
        this.runtimeBundle = runtimeBundle;
        this.runtimeProperties = runtimeProperties;
    }

    /**
     * Resolves and validates the runtime image required by local-docker generation: the configured digest when one is
     * pinned, otherwise the per-commit image of the model's source commit, the Artemis commit that the overlay keys
     * were verified against.
     *
     * @param sourceCommit Artemis source commit of the active model.
     * @return complete Artemis runtime source.
     * @throws ArtifactGenerationException if the selected source configures no value, or no digest is pinned and the
     *             model names no full source commit.
     */
    public ArtemisRuntimeSource resolveForLocalDocker(String sourceCommit) {
        GeneratedSnapshotMetadata metadata = runtimeBundle.snapshotMetadata();
        String configuredDigest = configuredDigest(metadata);
        requireValue(configuredDigest, sourceLabel(metadata));
        if (!NO_PINNED_DIGEST.equals(configuredDigest)) {
            return new ArtemisRuntimeSource(IMAGE_REPOSITORY, configuredDigest, null);
        }
        String imageTag = ArtemisRuntimeSource.commitTag(sourceCommit);
        requireValue(imageTag, "model.sourceCommitSha, which must be a full 40-character commit SHA when no image digest is pinned");
        return new ArtemisRuntimeSource(IMAGE_REPOSITORY, null, imageTag);
    }

    /**
     * Resolves the available runtime image for dev-ide without imposing remote-image requirements.
     *
     * @return Artemis runtime source with the pinned digest, if any, and no image tag.
     */
    public ArtemisRuntimeSource resolveForDevIde() {
        String configuredDigest = configuredDigest(runtimeBundle.snapshotMetadata());
        boolean pinned = configuredDigest != null && !configuredDigest.isBlank() && !NO_PINNED_DIGEST.equals(configuredDigest);
        return new ArtemisRuntimeSource(IMAGE_REPOSITORY, pinned ? configuredDigest : null, null);
    }

    /**
     * Reads the configured digest exclusively from active snapshot metadata or, in classpath mode, from the runtime
     * properties.
     *
     * @param metadata active metadata, or null for classpath mode.
     * @return configured digest value, possibly {@code null}.
     */
    private String configuredDigest(GeneratedSnapshotMetadata metadata) {
        return metadata == null ? runtimeProperties.imageDigest() : metadata.imageDigest();
    }

    /**
     * Builds an actionable source label for a missing image digest.
     *
     * @param metadata active metadata, or null for classpath mode.
     * @return actionable source label.
     */
    private String sourceLabel(GeneratedSnapshotMetadata metadata) {
        if (metadata != null) {
            return "active snapshot '" + metadata.snapshotId() + "' metadata.imageDigest"
                    + "; regenerate the snapshot with current extraction metadata";
        }
        return "property artemis.feature-model.runtime.image-digest";
    }

    /**
     * Rejects a missing required local-docker runtime value.
     *
     * @param value resolved value.
     * @param sourceLabel actionable source label.
     * @throws ArtifactGenerationException if the value is null or blank.
     */
    private void requireValue(String value, String sourceLabel) {
        if (value == null || value.isBlank()) {
            throw ArtifactGenerationException.missingArtemisRuntimeValue(sourceLabel);
        }
    }
}
