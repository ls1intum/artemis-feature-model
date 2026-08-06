package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/** Resolves Artemis runtime provenance from the active snapshot or the classpath runtime properties. */
@Component
public class ArtemisRuntimeSourceResolver {

    private static final String IMAGE_REPOSITORY = "ghcr.io/ls1intum/artemis";

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
     * Resolves and validates the provenance required by local-docker generation.
     *
     * @return complete Artemis runtime source.
     * @throws ArtifactGenerationException if the selected source lacks a required value.
     */
    public ArtemisRuntimeSource resolveForLocalDocker() {
        GeneratedSnapshotMetadata metadata = runtimeBundle.snapshotMetadata();
        ArtemisRuntimeSource source = metadata == null ? fromClasspath() : fromSnapshot(metadata);
        requireValue(source.sourceCommit(), sourceLabel(metadata, "sourceCommit"));
        requireValue(source.imageDigest(), sourceLabel(metadata, "imageDigest"));
        return source;
    }

    /**
     * Resolves available provenance for dev-ide without imposing remote-image requirements.
     *
     * @return Artemis runtime source whose values may be absent for a legacy snapshot.
     */
    public ArtemisRuntimeSource resolveForDevIde() {
        GeneratedSnapshotMetadata metadata = runtimeBundle.snapshotMetadata();
        return metadata == null ? fromClasspath() : fromSnapshot(metadata);
    }

    /**
     * Builds a source exclusively from active snapshot metadata.
     *
     * @param metadata active snapshot metadata.
     * @return snapshot-derived runtime source.
     */
    private ArtemisRuntimeSource fromSnapshot(GeneratedSnapshotMetadata metadata) {
        return new ArtemisRuntimeSource(metadata.sourceCommit(), IMAGE_REPOSITORY, metadata.imageDigest());
    }

    /**
     * Builds a source exclusively from classpath runtime properties.
     *
     * @return classpath-derived runtime source.
     */
    private ArtemisRuntimeSource fromClasspath() {
        return new ArtemisRuntimeSource(runtimeProperties.sourceCommit(), IMAGE_REPOSITORY, runtimeProperties.imageDigest());
    }

    /**
     * Builds an actionable source label for a missing value.
     *
     * @param metadata active metadata, or null for classpath mode.
     * @param field missing Java field name.
     * @return actionable source label.
     */
    private String sourceLabel(GeneratedSnapshotMetadata metadata, String field) {
        if (metadata != null) {
            return "active snapshot '" + metadata.snapshotId() + "' metadata." + field
                    + "; regenerate the snapshot with current extraction metadata";
        }
        String property = "sourceCommit".equals(field) ? "source-commit" : "image-digest";
        return "property artemis.feature-model.runtime." + property;
    }

    /**
     * Rejects a missing required local-docker provenance value.
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
