package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the fail-soft contract on a fixture with an unparseable constants class and several missing anchor files:
 * failing extractors become error report items while the remaining extractors still contribute their candidates.
 */
class ExtractionFailSoftTest {

    private static final Path BROKEN_FIXTURE_PATH = Path.of("src/test/resources/extraction/broken-artemis");

    private static final Path COMPLETE_FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void unparseableSourceBecomesErrorItemAndScanCompletes() {
        FeatureExtractionService service = new FeatureExtractionService(new ObjectMapper());
        FeatureExtractionService.Outcome outcome = service.scan(new LocalArtemisSourceRepository(BROKEN_FIXTURE_PATH),
                ExtractionTestModels.minimalCuratedModel(), new ArtemisConfigKeyCatalog("0.0.1-test", "fixturepin", "synthetic", null));

        assertThat(outcome.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("backend constants");
        });
        assertThat(outcome.candidates()).extracting(FeatureCandidate::id).contains("toggle:ToggleOne", "toggle:ToggleTwo");
        assertThat(outcome.items()).noneSatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_FE_BE_MIRROR_MISMATCH));
    }

    @Test
    void perFileConditionFailureIsReportedWithoutDiscardingSiblingFacts() throws IOException {
        Path checkout = temporaryDirectory.resolve("per-file-failure");
        copyFixture(COMPLETE_FIXTURE_PATH, checkout);
        Path brokenCondition = checkout.resolve("src/main/java/de/tum/cit/aet/artemis/alpha/config/AlphaEnabled.java");
        Files.writeString(brokenCondition, "this is not Java source");

        ConditionClassScan.Result result = new ConditionClassScan().scan(new LocalArtemisSourceRepository(checkout));

        assertThat(result.conditions()).extracting(ConditionClassScan.ScannedCondition::className).contains("BetaEnabled").doesNotContain("AlphaEnabled");
        assertThat(result.errors()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.subject()).isEqualTo("src/main/java/de/tum/cit/aet/artemis/alpha/config/AlphaEnabled.java");
            assertThat(item.message()).startsWith("Could not parse condition class candidate:");
        });
    }

    /**
     * Copies one source fixture into a writable temporary checkout.
     *
     * @param source fixture root.
     * @param target temporary checkout root.
     * @throws IOException if a fixture entry cannot be copied.
     */
    private void copyFixture(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                }
                else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }
}
