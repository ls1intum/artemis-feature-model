package de.tum.cit.aet.artemis.featuremodel.extraction.source;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/** Tests the checkout boundary enforced around fallback candidates returned by a source repository. */
class ArtemisSourceLocatorTest {

    @Test
    void rejectsFallbackCandidateOutsideTheConventionOwnedRoot() {
        ArtemisSourceRepository source = new OutsideCandidateSourceRepository();

        assertThatThrownBy(() -> new ArtemisSourceLocator().locate(source, ArtemisSourceConventions.Files.SERVER_CONSTANTS,
                "symbol prefix " + ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX, content -> true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes fallback root").hasMessageContaining("../outside/Constants.java");
    }

    /** Repository fixture that simulates a compromised path result from an implementation boundary. */
    private static final class OutsideCandidateSourceRepository implements ArtemisSourceRepository {

        @Override
        public String commit() {
            return UNKNOWN_COMMIT;
        }

        @Override
        public Boolean workingTreeDirty() {
            return null;
        }

        @Override
        public Path root() {
            return Path.of(".");
        }

        @Override
        public boolean fileExists(String relativePath) {
            return false;
        }

        @Override
        public String readFile(String relativePath) {
            throw new AssertionError("An out-of-bound candidate must be rejected before it is read.");
        }

        @Override
        public List<String> readLines(String relativePath) {
            throw new AssertionError("An out-of-bound candidate must be rejected before it is read.");
        }

        @Override
        public List<String> findFiles(String relativeDirectory, String fileNameSuffix) {
            return List.of();
        }

        @Override
        public List<String> findFilesByName(String relativeDirectory, String fileName) {
            return List.of("../outside/Constants.java");
        }

        @Override
        public Optional<String> firstExisting(List<String> relativePaths) {
            return Optional.empty();
        }
    }
}
