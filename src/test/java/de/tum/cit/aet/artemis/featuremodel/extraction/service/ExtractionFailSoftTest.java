package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;
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
        ExtractedSourceFacts outcome = service.scan(new LocalArtemisSourceRepository(BROKEN_FIXTURE_PATH));

        assertThat(outcome.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("server constants");
        });
        assertThat(outcome.candidates()).extracting(FeatureCandidate::id).contains("toggle:ToggleOne", "toggle:ToggleTwo");
        assertThat(outcome.items()).noneSatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH));
    }

    @Test
    void perFileConditionFailureIsReportedWithoutDiscardingSiblingFacts() throws IOException {
        Path checkout = temporaryDirectory.resolve("per-file-failure");
        copyFixture(COMPLETE_FIXTURE_PATH, checkout);
        String brokenConditionPath = "src/main/java/de/tum/cit/aet/artemis/alpha/config/AlphaEnabled.java";
        Path brokenCondition = checkout.resolve(brokenConditionPath);
        Files.writeString(brokenCondition, "this is not Java source");

        SourceScanResult<ConditionClassScan.Result> result = new ConditionClassScan().scan(new LocalArtemisSourceRepository(checkout));

        assertThat(result.facts().conditions()).extracting(ConditionClassScan.ScannedCondition::className).contains("BetaEnabled").doesNotContain("AlphaEnabled");
        assertThat(result.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.subject()).isEqualTo(brokenConditionPath);
            assertThat(item.message()).startsWith("Could not parse condition class candidate:");
        });

        ExtractedSourceFacts outcome = new FeatureExtractionService(new ObjectMapper()).scan(new LocalArtemisSourceRepository(checkout));
        assertThat(outcome.items()).filteredOn(item -> ReportItem.CODE_EXTRACTOR_ERROR.equals(item.code()) && brokenConditionPath.equals(item.subject()))
                .singleElement();
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
