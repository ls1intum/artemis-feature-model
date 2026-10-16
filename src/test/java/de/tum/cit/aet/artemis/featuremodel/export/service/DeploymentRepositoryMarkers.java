package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deployment repository marker content for publish tests. The valid marker derives its package contract from the
 * remote-ansible package version, so a version bump never needs a test edit.
 */
public final class DeploymentRepositoryMarkers {

    private DeploymentRepositoryMarkers() {
    }

    /**
     * Returns the marker file name at the repository root.
     *
     * @return marker file name.
     */
    public static String fileName() {
        return DeploymentRepositoryPublisher.MARKER_FILE_NAME;
    }

    /**
     * Builds a marker declaring the expected kind and the package contract this instance publishes.
     *
     * @return valid marker JSON.
     */
    public static String validMarker() {
        return marker(DeploymentRepositoryPublisher.MARKER_KIND, String.valueOf(DeploymentRepositoryPublisher.expectedPackageContract()));
    }

    /**
     * Builds a marker with the given kind and raw JSON package contract value.
     *
     * @param kind marker kind.
     * @param packageContractJson raw JSON value of the package contract.
     * @return marker JSON.
     */
    public static String marker(String kind, String packageContractJson) {
        return "{ \"kind\": \"" + kind + "\", \"packageContract\": " + packageContractJson + " }\n";
    }

    /**
     * Writes the valid marker into a work tree root.
     *
     * @param workTree work tree root.
     * @throws IOException if the file cannot be written.
     */
    public static void writeValidMarker(Path workTree) throws IOException {
        Files.writeString(workTree.resolve(fileName()), validMarker());
    }
}
