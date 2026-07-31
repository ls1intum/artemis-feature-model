package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Contract of the model assembly stage, written as {@code model/model-result.json}. It records which scan and which
 * manifest the generated model was assembled from, so the workflow and packaging stages can reject a model that no
 * longer matches its inputs.
 *
 * @param schemaVersion schema version of this envelope.
 * @param extractorVersion version of the extraction pipeline that assembled the model.
 * @param artemisCommit resolved git commit the model was assembled from.
 * @param scanDigest payload digest of the consumed scan.
 * @param manifestDigest digest of the consumed scope manifest.
 * @param generatedModelDigest digest of the generated feature model file.
 * @param modelIntegrityValid whether the generated model passed the shared structural integrity validation.
 * @param curation manifest classification section of this run.
 */
public record ModelResult(int schemaVersion, String extractorVersion, String artemisCommit, String scanDigest, String manifestDigest,
        String generatedModelDigest, boolean modelIntegrityValid, CurationReport curation) {

    /** Current schema version of the model envelope. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
