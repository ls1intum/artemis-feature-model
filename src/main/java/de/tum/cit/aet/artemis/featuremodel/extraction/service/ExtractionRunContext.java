package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.Objects;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/**
 * Per-command manifest boundary derived from one byte read: exact bytes, parsed content, digest, pinned commit, and
 * commit-scoped artifact layout cannot drift apart within a command.
 *
 * @param manifestBytes exact bytes read from the configured manifest.
 * @param manifest parsed and validated manifest from those bytes.
 * @param manifestDigest SHA-256 digest of those bytes.
 * @param artemisCommit pinned Artemis commit from the parsed manifest.
 * @param layout artifact layout derived from the output root and pinned commit.
 */
record ExtractionRunContext(byte[] manifestBytes, FeatureScopeManifest manifest, String manifestDigest, String artemisCommit,
        ExtractionArtifactLayout layout) {

    ExtractionRunContext {
        manifestBytes = manifestBytes.clone();
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(manifestDigest);
        Objects.requireNonNull(artemisCommit);
        Objects.requireNonNull(layout);
        if (!artemisCommit.equals(manifest.artemisCommitSha())) {
            throw new IllegalArgumentException("The run context commit must equal the parsed manifest commit.");
        }
    }

    @Override
    public byte[] manifestBytes() {
        return manifestBytes.clone();
    }
}
