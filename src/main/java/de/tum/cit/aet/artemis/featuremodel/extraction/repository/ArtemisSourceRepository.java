package de.tum.cit.aet.artemis.featuremodel.extraction.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Read-only access boundary to an Artemis source checkout. All extractor file access goes through this interface so
 * the pipeline can run against the real checkout and against synthetic test fixtures alike.
 */
public interface ArtemisSourceRepository {

    /**
     * Returns the resolved git commit of the checkout.
     *
     * @return full commit hash, or {@code unknown} when the checkout is not a git work tree.
     */
    String commit();

    /**
     * Indicates whether the checkout had uncommitted changes at scan time.
     *
     * @return true if the work tree was dirty, null when the state could not be resolved.
     */
    Boolean workingTreeDirty();

    /**
     * Returns the absolute checkout root path.
     *
     * @return checkout root.
     */
    Path root();

    /**
     * Checks whether a checkout-relative file exists.
     *
     * @param relativePath path relative to the checkout root.
     * @return true if the path exists as a regular file.
     */
    boolean fileExists(String relativePath);

    /**
     * Reads a checkout-relative file as UTF-8 text.
     *
     * @param relativePath path relative to the checkout root.
     * @return file content.
     * @throws IOException if the file cannot be read.
     */
    String readFile(String relativePath) throws IOException;

    /**
     * Reads a checkout-relative file as UTF-8 lines.
     *
     * @param relativePath path relative to the checkout root.
     * @return file lines in order.
     * @throws IOException if the file cannot be read.
     */
    List<String> readLines(String relativePath) throws IOException;

    /**
     * Lists all regular files under a checkout-relative directory whose file name matches the given suffix, sorted by
     * checkout-relative path for deterministic iteration order.
     *
     * @param relativeDirectory directory relative to the checkout root.
     * @param fileNameSuffix required file name suffix, for example {@code .java}.
     * @return sorted checkout-relative paths; empty when the directory does not exist.
     * @throws IOException if the directory cannot be traversed.
     */
    List<String> findFiles(String relativeDirectory, String fileNameSuffix) throws IOException;

    /**
     * Finds all regular files with the given file name under a checkout-relative directory, sorted by
     * checkout-relative path.
     *
     * @param relativeDirectory directory relative to the checkout root.
     * @param fileName exact file name to match.
     * @return sorted checkout-relative paths; empty when the directory does not exist.
     * @throws IOException if the directory cannot be traversed.
     */
    List<String> findFilesByName(String relativeDirectory, String fileName) throws IOException;

    /**
     * Resolves the first existing file among the given checkout-relative candidates.
     *
     * @param relativePaths candidate paths relative to the checkout root.
     * @return first existing path, or empty when none exists.
     */
    Optional<String> firstExisting(List<String> relativePaths);
}
