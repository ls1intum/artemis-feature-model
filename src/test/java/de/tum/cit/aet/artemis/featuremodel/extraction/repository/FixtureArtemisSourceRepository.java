package de.tum.cit.aet.artemis.featuremodel.extraction.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Reads a checked-in fixture directory while reporting a chosen git state. Fixtures are ordinary directories inside
 * this repository rather than git work trees, so tests that exercise the pinned-source preflight declare the commit
 * and the working tree state instead of creating a throwaway repository.
 *
 * @param delegate repository reading the fixture files.
 * @param commit commit the fixture claims to be at.
 * @param workingTreeDirty working tree state the fixture claims, or null when it cannot be resolved.
 */
public record FixtureArtemisSourceRepository(ArtemisSourceRepository delegate, String commit, Boolean workingTreeDirty) implements ArtemisSourceRepository {

    /**
     * Creates a clean fixture repository at the given commit.
     *
     * @param fixturePath fixture directory.
     * @param commit commit the fixture claims to be at.
     * @return fixture repository reporting a clean working tree.
     */
    public static FixtureArtemisSourceRepository cleanAt(Path fixturePath, String commit) {
        return new FixtureArtemisSourceRepository(new LocalArtemisSourceRepository(fixturePath), commit, false);
    }

    @Override
    public Path root() {
        return delegate.root();
    }

    @Override
    public boolean fileExists(String relativePath) {
        return delegate.fileExists(relativePath);
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        return delegate.readFile(relativePath);
    }

    @Override
    public List<String> readLines(String relativePath) throws IOException {
        return delegate.readLines(relativePath);
    }

    @Override
    public List<String> findFiles(String relativeDirectory, String fileNameSuffix) throws IOException {
        return delegate.findFiles(relativeDirectory, fileNameSuffix);
    }

    @Override
    public List<String> findFilesByName(String relativeDirectory, String fileName) throws IOException {
        return delegate.findFilesByName(relativeDirectory, fileName);
    }

    @Override
    public Optional<String> firstExisting(List<String> relativePaths) {
        return delegate.firstExisting(relativePaths);
    }
}
