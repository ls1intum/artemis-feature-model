package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection.ConfigurationPropertiesPrefix;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Covers placeholder-key extraction, prefix declarations, guard recording, marker filtering, and fail-soft parsing. */
class ConfigInjectionScanTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void recordsGuardedValueKeysWithoutDefaultsAndPrefixesPerFile() throws Exception {
        SourceScanResult<List<ExtractedConfigInjection>> scan = new ConfigInjectionScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));

        assertThat(scan.diagnostics()).isEmpty();
        assertThat(scan.facts()).extracting(ExtractedConfigInjection::file).isSorted();
        ExtractedConfigInjection connector = injectionOf(scan.facts(), "AlphaConnectorConfiguration.java");
        assertThat(connector.packageName()).isEqualTo("de.tum.cit.aet.artemis.alpha.config");
        assertThat(connector.conditionGuards()).containsExactly("AlphaEnabled");
        assertThat(connector.valueKeys()).as("default values after the colon are stripped").containsExactly("artemis.alpha.timeout", "artemis.alpha.token",
                "artemis.shared.instance-name");
        assertThat(connector.propertyPrefixes()).isEmpty();

        ExtractedConfigInjection properties = injectionOf(scan.facts(), "AlphaConnectorProperties.java");
        assertThat(properties.conditionGuards()).containsExactly("AlphaEnabled");
        assertThat(properties.valueKeys()).isEmpty();
        assertThat(properties.propertyPrefixes()).containsExactly(new ConfigurationPropertiesPrefix("artemis.alpha.connector", "AlphaConnectorProperties"));

        ExtractedConfigInjection shared = injectionOf(scan.facts(), "SharedInstanceConfiguration.java");
        assertThat(shared.conditionGuards()).isEmpty();
        assertThat(shared.valueKeys()).containsExactly("artemis.shared.instance-name");
    }

    @Test
    void skipsFilesWithoutAnInjectionMarker() throws Exception {
        SourceScanResult<List<ExtractedConfigInjection>> scan = new ConfigInjectionScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));

        assertThat(scan.facts()).extracting(ExtractedConfigInjection::file).noneMatch(file -> file.endsWith("AlphaResource.java"))
                .noneMatch(file -> file.endsWith("AlphaEnabled.java"));
    }

    @Test
    void recordsProfileGuardLiteralsAndConstantReferences() throws Exception {
        writeSource("de/tum/cit/aet/artemis/omega/OmegaScheduler.java", """
                package de.tum.cit.aet.artemis.omega;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.context.annotation.Profile;

                @Profile({ "scheduling", PROFILE_CORE, Constants.PROFILE_OMEGA })
                public class OmegaScheduler {

                    @Value("${artemis.omega.first-url}/api/${artemis.omega.second-url:https://fallback.example.org}")
                    private String endpoint;
                }
                """);

        SourceScanResult<List<ExtractedConfigInjection>> scan = new ConfigInjectionScan().scan(new LocalArtemisSourceRepository(temporaryDirectory));

        assertThat(scan.diagnostics()).isEmpty();
        assertThat(scan.facts()).singleElement().satisfies(injection -> {
            assertThat(injection.profileGuards()).containsExactly("PROFILE_CORE", "PROFILE_OMEGA", "scheduling");
            assertThat(injection.valueKeys()).as("every placeholder of one value expression is collected")
                    .containsExactly("artemis.omega.first-url", "artemis.omega.second-url");
        });
    }

    @Test
    void reportsAnUnparseableFileAndKeepsScanningSiblings() throws Exception {
        writeSource("de/tum/cit/aet/artemis/omega/Broken.java", """
                package de.tum.cit.aet.artemis.omega;

                public class Broken {
                    @Value("${artemis.omega.url}")
                """);
        writeSource("de/tum/cit/aet/artemis/omega/Working.java", """
                package de.tum.cit.aet.artemis.omega;

                import org.springframework.beans.factory.annotation.Value;

                public class Working {

                    @Value("${artemis.omega.url}")
                    private String url;
                }
                """);

        SourceScanResult<List<ExtractedConfigInjection>> scan = new ConfigInjectionScan().scan(new LocalArtemisSourceRepository(temporaryDirectory));

        assertThat(scan.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.subject()).endsWith("Broken.java");
        });
        assertThat(scan.facts()).singleElement().satisfies(injection -> assertThat(injection.file()).endsWith("Working.java"));
    }

    /**
     * Writes one synthetic Java source under the temporary checkout.
     *
     * @param relativePath path below {@code src/main/java}.
     * @param content Java source text.
     * @throws Exception if the file cannot be written.
     */
    private void writeSource(String relativePath, String content) throws Exception {
        Path file = temporaryDirectory.resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * Selects the injection record of one file by its file name.
     *
     * @param injections scanned injections.
     * @param fileName expected file name suffix.
     * @return matching injection record.
     */
    private ExtractedConfigInjection injectionOf(List<ExtractedConfigInjection> injections, String fileName) {
        return injections.stream().filter(injection -> injection.file().endsWith(fileName)).findFirst().orElseThrow();
    }
}
