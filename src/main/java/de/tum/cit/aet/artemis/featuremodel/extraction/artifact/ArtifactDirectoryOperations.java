package de.tum.cit.aet.artemis.featuremodel.extraction.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;

/** Owns recursive deletion and ordered stage invalidation for extraction artifact directories. */
public final class ArtifactDirectoryOperations {

    /**
     * Removes the output of a stage and every downstream stage in layout-defined order.
     *
     * @param layout output layout of the run.
     * @param stage stage about to write.
     * @throws IOException if a directory cannot be removed.
     */
    public void invalidateFrom(ExtractionArtifactLayout layout, ExtractionStage stage) throws IOException {
        for (Path directory : layout.directoriesInvalidatedBy(stage)) {
            deleteRecursively(directory);
        }
    }

    /**
     * Deletes one file tree from children to root. Missing paths are ignored.
     *
     * @param path file tree to delete.
     * @throws IOException if a path cannot be deleted.
     */
    public void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
