package de.tum.cit.aet.artemis.featuremodel.snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Helpers that write small, internally consistent feature model snapshot folders for tests. The synthetic model and
 * workflow pass structural integrity validation, so tests do not depend on or mutate the runtime classpath JSON.
 */
public final class SnapshotTestFixtures {

    public static final String MODEL_FILE = "feature-model.json";

    public static final String WORKFLOW_FILE = "guided-workflow.json";

    public static final String REPORT_FILE = "generation-report.json";

    public static final String CHECKSUM_FILE = "checksum.txt";

    public static final String METADATA_FILE = "metadata.json";

    public static final String MODEL_ID = "snapshot-model";

    public static final String MODEL_VERSION = "1.0.0";

    private SnapshotTestFixtures() {
    }

    /**
     * Writes a complete, valid snapshot folder with a matching workflow, metadata, report, and correct checksum.
     *
     * @param directory snapshot folder to create.
     * @return the created snapshot folder.
     */
    public static Path writeValidSnapshot(Path directory) {
        return writeSnapshot(directory, MODEL_ID, MODEL_VERSION, MODEL_ID, MODEL_VERSION, true, true, true);
    }

    /**
     * Writes a snapshot folder with configurable model/workflow ids and optional files.
     *
     * @param directory snapshot folder to create.
     * @param modelId feature model id used in the model file and metadata.
     * @param modelVersion feature model version used in the model file and metadata.
     * @param workflowModelId model id the workflow targets.
     * @param workflowModelVersion model version the workflow targets.
     * @param withChecksum whether to write a correct checksum file.
     * @param withReport whether to write a generation report file.
     * @param withMetadata whether to write a metadata file.
     * @return the created snapshot folder.
     */
    public static Path writeSnapshot(Path directory, String modelId, String modelVersion, String workflowModelId, String workflowModelVersion,
            boolean withChecksum, boolean withReport, boolean withMetadata) {
        try {
            Files.createDirectories(directory);
            Path modelFile = directory.resolve(MODEL_FILE);
            Files.writeString(modelFile, modelJson(modelId, modelVersion));
            Files.writeString(directory.resolve(WORKFLOW_FILE), workflowJson(workflowModelId, workflowModelVersion));
            if (withMetadata) {
                Files.writeString(directory.resolve(METADATA_FILE), metadataJson(modelId, directory.getFileName().toString(), modelVersion));
            }
            if (withReport) {
                Files.writeString(directory.resolve(REPORT_FILE), "{\n  \"generatedAt\": \"2026-06-13T00:00:00Z\",\n  \"warnings\": []\n}\n");
            }
            if (withChecksum) {
                Files.writeString(directory.resolve(CHECKSUM_FILE), "sha256:" + sha256Hex(modelFile) + "\n");
            }
            return directory;
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not write snapshot fixture at " + directory + ".", e);
        }
    }

    /**
     * Overwrites the checksum file with a deliberately wrong value.
     *
     * @param directory snapshot folder.
     */
    public static void corruptChecksum(Path directory) {
        try {
            Files.writeString(directory.resolve(CHECKSUM_FILE), "sha256:0000000000000000000000000000000000000000000000000000000000000000\n");
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not corrupt checksum at " + directory + ".", e);
        }
    }

    /**
     * Computes the lowercase SHA-256 hex digest of a file.
     *
     * @param file file to hash.
     * @return lowercase hex digest.
     */
    public static String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not hash " + file + ".", e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static String modelJson(String modelId, String version) {
        return """
                {
                  "model": { "id": "%s", "name": "Snapshot Model", "version": "%s", "status": "development", "sourceCommitSha": "abc123" },
                  "features": [
                    { "id": "artemis", "name": "Artemis", "kind": "root", "selectable": false, "defaultState": "not_applicable" },
                    { "id": "lecture", "name": "Lecture", "kind": "module", "selectable": true, "defaultState": "enabled" }
                  ],
                  "relations": [
                    { "parentId": "artemis", "childId": "lecture", "relationType": "optional", "groupType": null, "order": 1 }
                  ],
                  "constraints": []
                }
                """.formatted(modelId, version);
    }

    private static String workflowJson(String featureModelId, String featureModelVersion) {
        return """
                {
                  "workflow": {
                    "id": "snapshot-workflow", "name": "Snapshot Workflow", "version": "1.0.0",
                    "featureModelId": "%s", "featureModelVersion": "%s", "defaultTemplateId": "custom"
                  },
                  "useCaseTemplates": [
                    { "id": "custom", "label": "Custom", "description": "Custom template.",
                      "selectedFeatureIds": ["lecture"], "deselectedFeatureIds": [], "recommendedStepIds": ["content"],
                      "consequences": [], "warnings": [] }
                  ],
                  "steps": [
                    { "id": "content", "title": "Content", "order": 1, "description": "Choose content.", "decisions": [] }
                  ],
                  "finalReviewGroups": [
                    { "id": "summary", "title": "Summary", "order": 1, "featureIds": ["lecture"] }
                  ]
                }
                """.formatted(featureModelId, featureModelVersion);
    }

    private static String metadataJson(String modelId, String snapshotId, String version) {
        return """
                {
                  "modelId": "%s",
                  "snapshotId": "%s",
                  "version": "%s",
                  "status": "development",
                  "sourceRepo": "ls1intum/Artemis",
                  "sourceRef": "develop",
                  "sourceCommit": "abc123",
                  "extractorVersion": "feature-model-extractor@0.2.0"
                }
                """.formatted(modelId, snapshotId, version);
    }
}
