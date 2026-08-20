package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Verifies before any command consumes the checkout that a source revision can be derived from it and attributed to
 * exactly one immutable commit: the checkout must be a git work tree, its working tree must be clean, and when the
 * caller supplies an externally expected revision — a CI validation pin or a dispatch input — the derived revision
 * must equal it. Extraction findings are only meaningful when they can be attributed to one immutable commit, so an
 * unresolvable revision or a dirty working tree stops the run instead of producing artifacts with a false identity.
 */
class ArtemisSourcePreflight {

    /**
     * Verifies the checkout and its derived revision.
     *
     * @param source Artemis source repository the command is about to consume.
     * @param expectedArtemisSha externally supplied revision the checkout must be at, or null when the caller has no
     *            expectation and the derived revision stands on its own.
     * @throws SourcePreflightException if no revision can be derived, the checkout is not clean, or the derived
     *             revision differs from the expected one.
     */
    void verify(ArtemisSourceRepository source, String expectedArtemisSha) {
        String commit = source.commit();
        if (ArtemisSourceRepository.UNKNOWN_COMMIT.equals(commit)) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " is not a git work tree, so no source revision can be "
                    + "derived for this run.");
        }
        if (expectedArtemisSha != null && !expectedArtemisSha.equals(commit)) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " is at commit " + commit + " but this run expects "
                    + expectedArtemisSha + ". Check out the expected commit, or drop the expectation for an unpinned local run.");
        }
        if (source.workingTreeDirty() == null) {
            throw new SourcePreflightException(
                    "The working tree state of the Artemis checkout at " + source.root() + " could not be resolved, so this run cannot be attributed to "
                            + commit + ".");
        }
        if (source.workingTreeDirty()) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " has uncommitted changes, so this run would not describe commit "
                    + commit + ". Commit, stash, or discard the changes.");
        }
    }
}
