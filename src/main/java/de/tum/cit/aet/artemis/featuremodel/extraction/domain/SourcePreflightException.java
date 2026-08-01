package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Raised when the local Artemis checkout is not the exact source the manifest selects: it is not a git work tree, its
 * {@code HEAD} is another commit, or its working tree carries uncommitted changes. The scan never starts in that
 * case, because its findings could not be attributed to the pinned commit.
 */
public class SourcePreflightException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the rejected checkout state and how to reach the pinned source.
     */
    public SourcePreflightException(String message) {
        super(message);
    }
}
