package de.tum.cit.aet.artemis.featuremodel.snapshot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request to import a local snapshot folder into the application data root.
 *
 * @param sourcePath local folder path to import the snapshot from.
 * @param snapshotId optional target snapshot id; defaults to the source folder name when blank.
 * @param overwrite whether to replace an existing snapshot with the same id; defaults to false when omitted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportSnapshotRequest(String sourcePath, String snapshotId, Boolean overwrite) {

    /**
     * Defaults an omitted overwrite flag to false so the request can be sent without it.
     *
     * @param sourcePath local folder path to import the snapshot from.
     * @param snapshotId optional target snapshot id.
     * @param overwrite whether to replace an existing snapshot with the same id.
     */
    public ImportSnapshotRequest {
        if (overwrite == null) {
            overwrite = Boolean.FALSE;
        }
    }
}
