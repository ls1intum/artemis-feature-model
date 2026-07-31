package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Raised when the manifest does not describe the scanned source completely. The model assembly writes its diagnostics
 * first and then fails, so a maintainer sees every open decision while no artifact of the incomplete curation exists.
 */
public class ManifestConformanceException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message summary of the findings that block the run.
     */
    public ManifestConformanceException(String message) {
        super(message);
    }
}
