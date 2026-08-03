package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;

/** Characterizes the source-target fallback behavior that the source boundary must preserve or make fail-closed. */
class SourceLocationCharacterizationTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void relocatedConstantsAreFoundByNameAndRequiredSymbol() throws IOException {
        Path checkout = copyFixture("relocated");
        Path preferred = checkout.resolve(BackendConstantScan.DEFAULT_CONSTANTS_PATH);
        Path relocated = checkout.resolve("src/main/java/relocated/Constants.java");
        Files.createDirectories(relocated.getParent());
        Files.move(preferred, relocated);

        BackendConstantScan.Result baseline = new BackendConstantScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));
        BackendConstantScan.Result result = new BackendConstantScan().scan(new LocalArtemisSourceRepository(checkout));

        assertThat(result.file()).isEqualTo("src/main/java/relocated/Constants.java");
        assertThat(result.constants()).isEqualTo(baseline.constants());
    }

    @Test
    void missingConstantsProduceAnActionableControlledFailure() throws IOException {
        Path checkout = copyFixture("missing");
        Files.delete(checkout.resolve(BackendConstantScan.DEFAULT_CONSTANTS_PATH));

        assertThatThrownBy(() -> new BackendConstantScan().scan(new LocalArtemisSourceRepository(checkout))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Constants.java").hasMessageContaining("MODULE_FEATURE_").hasMessageContaining("src/main/java");
    }

    @Test
    void ambiguousConstantsFallbackCurrentlySelectsTheFirstSortedVerifiedMatch() throws IOException {
        Path checkout = copyFixture("ambiguous");
        Path preferred = checkout.resolve(BackendConstantScan.DEFAULT_CONSTANTS_PATH);
        Path first = checkout.resolve("src/main/java/a/Constants.java");
        Path second = checkout.resolve("src/main/java/b/Constants.java");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.copy(preferred, first);
        Files.copy(preferred, second);
        Files.delete(preferred);

        BackendConstantScan.Result result = new BackendConstantScan().scan(new LocalArtemisSourceRepository(checkout));

        assertThat(result.file()).isEqualTo("src/main/java/a/Constants.java");
    }

    /**
     * Copies the mini-Artemis fixture into one writable temporary checkout.
     *
     * @param name checkout directory name.
     * @return copied checkout root.
     * @throws IOException if a fixture entry cannot be copied.
     */
    private Path copyFixture(String name) throws IOException {
        Path target = temporaryDirectory.resolve(name);
        try (var paths = Files.walk(FIXTURE_PATH)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(FIXTURE_PATH.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                }
                else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return target;
    }
}
