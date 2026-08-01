package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Verifies before any scan that the local checkout is exactly the source the manifest selects. Extraction findings are
 * only meaningful when they can be attributed to one immutable commit, so a checkout at another commit, an unresolved
 * commit, or a dirty working tree stops the run instead of producing artifacts that claim the pinned commit.
 */
class ArtemisSourcePreflight {

    /**
     * Verifies the checkout against the manifest pin.
     *
     * @param source Artemis source repository about to be scanned.
     * @param artemisCommitSha commit the manifest pins.
     * @throws SourcePreflightException if the checkout is not the pinned commit or is not clean.
     */
    void verify(ArtemisSourceRepository source, String artemisCommitSha) {
        String commit = source.commit();
        if (ArtemisSourceRepository.UNKNOWN_COMMIT.equals(commit)) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " is not a git work tree, so it cannot be verified against the "
                    + "manifest commit " + artemisCommitSha + ".");
        }
        if (!artemisCommitSha.equals(commit)) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " is at commit " + commit + " but the manifest pins "
                    + artemisCommitSha + ". Check out the pinned commit, or update artemisCommitSha deliberately.");
        }
        if (source.workingTreeDirty() == null) {
            throw new SourcePreflightException(
                    "The working tree state of the Artemis checkout at " + source.root() + " could not be resolved, so the scan cannot be attributed to "
                            + artemisCommitSha + ".");
        }
        if (source.workingTreeDirty()) {
            throw new SourcePreflightException("The Artemis checkout at " + source.root() + " has uncommitted changes, so a scan would not describe commit "
                    + artemisCommitSha + ". Commit, stash, or discard the changes.");
        }
    }
}
