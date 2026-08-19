package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.Objects;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/**
 * Per-command manifest boundary derived from one byte read and one verified checkout: exact manifest bytes, parsed
 * content, digest, derived source revision, and revision-scoped artifact layout cannot drift apart within a command.
 *
 * @param manifestBytes exact bytes read from the configured manifest.
 * @param manifest parsed and validated manifest from those bytes.
 * @param manifestDigest SHA-256 digest of those bytes.
 * @param artemisCommit source revision derived from the verified checkout's HEAD.
 * @param layout artifact layout derived from the output root and the derived revision.
 */
record ExtractionRunContext(byte[] manifestBytes, FeatureScopeManifest manifest, String manifestDigest, String artemisCommit,
        ExtractionArtifactLayout layout) {

    ExtractionRunContext {
        manifestBytes = manifestBytes.clone();
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(manifestDigest);
        Objects.requireNonNull(artemisCommit);
        Objects.requireNonNull(layout);
    }

    @Override
    public byte[] manifestBytes() {
        return manifestBytes.clone();
    }
}
