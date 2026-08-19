package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/** Characterizes the source-target fallback behavior that the source boundary must preserve or make fail-closed. */
class SourceLocationCharacterizationTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path EXTRACTION_SOURCE_PATH = Path.of("src/main/java/de/tum/cit/aet/artemis/featuremodel/extraction");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void relocatedConstantsAreFoundByNameAndRequiredSymbol() throws IOException {
        Path checkout = copyFixture("relocated");
        Path preferred = checkout.resolve(ArtemisSourceConventions.Files.SERVER_CONSTANTS.preferredPath());
        Path relocated = checkout.resolve("src/main/java/relocated/Constants.java");
        Files.createDirectories(relocated.getParent());
        Files.move(preferred, relocated);

        ServerConstantScan.Result baseline = new ServerConstantScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));
        ServerConstantScan.Result result = new ServerConstantScan().scan(new LocalArtemisSourceRepository(checkout));

        assertThat(result.file()).isEqualTo("src/main/java/relocated/Constants.java");
        assertThat(result.constants()).isEqualTo(baseline.constants());
    }

    @Test
    void missingConstantsProduceAnActionableControlledFailure() throws IOException {
        Path checkout = copyFixture("missing");
        Files.delete(checkout.resolve(ArtemisSourceConventions.Files.SERVER_CONSTANTS.preferredPath()));

        assertThatThrownBy(() -> new ServerConstantScan().scan(new LocalArtemisSourceRepository(checkout))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Constants.java").hasMessageContaining("MODULE_FEATURE_").hasMessageContaining("src/main/java");
    }

    @Test
    void ambiguousConstantsFallbackIsRejectedWithEveryVerifiedMatchNamed() throws IOException {
        Path checkout = copyFixture("ambiguous");
        Path preferred = checkout.resolve(ArtemisSourceConventions.Files.SERVER_CONSTANTS.preferredPath());
        Path first = checkout.resolve("src/main/java/a/Constants.java");
        Path second = checkout.resolve("src/main/java/b/Constants.java");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.copy(preferred, first);
        Files.copy(preferred, second);
        Files.delete(preferred);

        assertThatThrownBy(() -> new ServerConstantScan().scan(new LocalArtemisSourceRepository(checkout))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ambiguous server constants target").hasMessageContaining("src/main/java/a/Constants.java")
                .hasMessageContaining("src/main/java/b/Constants.java");
    }

    @Test
    void scannerImplementationsDoNotReintroduceConventionOwnedSourceRootLiterals() throws IOException {
        try (var paths = Files.walk(EXTRACTION_SOURCE_PATH)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.getFileName().toString().equals("ArtemisSourceConventions.java")).toList()) {
                assertThat(Files.readString(path)).as("source conventions in %s", path).doesNotContain("\"src/main/java", "\"src/main/resources",
                        "\"src/main/webapp", "\"docker\"");
            }
        }
    }

    @Test
    void extractionSourcesUseClientAndServerTerminology() throws IOException {
        List<String> retiredTerms = List.of("front" + "end", "back" + "end");
        try (var paths = Files.walk(EXTRACTION_SOURCE_PATH)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".java")).toList()) {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                String source = Files.readString(path).toLowerCase(Locale.ROOT);
                assertThat(retiredTerms).as("terminology in %s", path).noneMatch(fileName::contains).noneMatch(source::contains);
            }
        }
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
