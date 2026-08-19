package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.nio.file.Path;
import java.util.List;

/**
 * Output layout of one extraction run, derived from the output root and the source revision derived from the verified
 * checkout. Every extraction command resolves its directories through this layout instead of composing paths itself,
 * so each stage owns exactly one directory and no stage can write into another stage's output.
 *
 * @param root run directory, {@code <outputRoot>/<derived-source-revision>}.
 */
public record ExtractionArtifactLayout(Path root) {

    private static final String SCAN_DIRECTORY = "scan";

    private static final String MODEL_DIRECTORY = "model";

    private static final String WORKFLOW_DIRECTORY = "workflow";

    private static final String REPORT_DIRECTORY = "report";

    private static final String SNAPSHOT_DIRECTORY = "snapshot";

    /**
     * Derives the layout of one run.
     *
     * @param outputRoot root directory of all extraction runs, typically {@code build/feature-extraction}.
     * @param artemisCommitSha derived source revision the run is attributed to.
     * @return layout of the run directory.
     */
    public static ExtractionArtifactLayout forCommit(Path outputRoot, String artemisCommitSha) {
        return new ExtractionArtifactLayout(outputRoot.resolve(artemisCommitSha));
    }

    /**
     * Returns the directory owned by the scan command.
     *
     * @return raw source discovery outputs.
     */
    public Path scanDirectory() {
        return root.resolve(SCAN_DIRECTORY);
    }

    /**
     * Returns the directory owned by the model assembly command.
     *
     * @return generated model, catalog, and comparison outputs.
     */
    public Path modelDirectory() {
        return root.resolve(MODEL_DIRECTORY);
    }

    /**
     * Returns the directory owned by the workflow preparation command.
     *
     * @return prepared guided workflow and its validation output.
     */
    public Path workflowDirectory() {
        return root.resolve(WORKFLOW_DIRECTORY);
    }

    /**
     * Returns the directory owned by the snapshot packaging command for consolidated diagnostics.
     *
     * @return consolidated extraction report.
     */
    public Path reportDirectory() {
        return root.resolve(REPORT_DIRECTORY);
    }

    /**
     * Returns the published snapshot directory owned by the snapshot packaging command.
     *
     * @return importable snapshot folder.
     */
    public Path snapshotDirectory() {
        return root.resolve(SNAPSHOT_DIRECTORY);
    }

    /**
     * Returns the directories a command must clear before it writes: its own output plus every directory derived from
     * it. Clearing them keeps a failed or partial rerun from leaving a stale downstream artifact behind.
     *
     * @param stage command whose output is about to be rewritten.
     * @return directories to remove.
     */
    public List<Path> directoriesInvalidatedBy(ExtractionStage stage) {
        return switch (stage) {
            case SCAN -> List.of(scanDirectory(), modelDirectory(), workflowDirectory(), reportDirectory(), snapshotDirectory());
            case MODEL -> List.of(modelDirectory(), workflowDirectory(), reportDirectory(), snapshotDirectory());
            case WORKFLOW -> List.of(workflowDirectory(), reportDirectory(), snapshotDirectory());
            case PACKAGE -> List.of(reportDirectory(), snapshotDirectory());
        };
    }
}
