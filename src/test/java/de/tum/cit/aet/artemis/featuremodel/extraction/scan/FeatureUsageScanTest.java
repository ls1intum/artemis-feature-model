package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage.MethodLabel;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Covers class and method labels, condition and profile guards, malformed labels, marker filtering, ordering, and fail-soft parsing. */
class FeatureUsageScanTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path MALFORMED_FIXTURE = Path.of("src/test/resources/extraction/fixture-inputs/malformed-feature-usage");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void recordsClassLabelsMethodOverridesAndGuardsPerTypeInFileOrder() throws Exception {
        SourceScanResult<List<ExtractedFeatureUsage>> scan = new FeatureUsageScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));

        assertThat(scan.diagnostics()).isEmpty();
        assertThat(scan.facts()).extracting(ExtractedFeatureUsage::file).isSorted();
        assertThat(scan.facts()).extracting(ExtractedFeatureUsage::type).containsExactly("AlphaResource", "BetaResource", "CioneStatusResource");

        ExtractedFeatureUsage alpha = usageOf(scan.facts(), "AlphaResource");
        assertThat(alpha.file()).isEqualTo("src/main/java/de/tum/cit/aet/artemis/alpha/web/AlphaResource.java");
        assertThat(alpha.module()).isEqualTo("alpha");
        assertThat(alpha.classLabel()).isEqualTo("authoring/alpha-items");
        assertThat(alpha.methodLabels()).isEmpty();
        assertThat(alpha.conditionGuards()).as("only the type-level guard counts, not the method-level composite condition").containsExactly("AlphaEnabled");
        assertThat(alpha.profileGuards()).isEmpty();
        assertThat(alpha.line()).isEqualTo(19);
        assertThat(alpha.effectiveLabels()).containsExactly("authoring/alpha-items");

        ExtractedFeatureUsage beta = usageOf(scan.facts(), "BetaResource");
        assertThat(beta.module()).isEqualTo("beta");
        assertThat(beta.classLabel()).isEqualTo("review/beta-reviews");
        assertThat(beta.methodLabels()).containsExactly(new MethodLabel("exportReviews", "review/beta-exports", 22));
        assertThat(beta.conditionGuards()).isEmpty();
        assertThat(beta.effectiveLabels()).containsExactly("review/beta-exports", "review/beta-reviews");

        ExtractedFeatureUsage cione = usageOf(scan.facts(), "CioneStatusResource");
        assertThat(cione.module()).isEqualTo("core");
        assertThat(cione.classLabel()).isEqualTo("build/cione-status");
        assertThat(cione.profileGuards()).as("constant names are recorded as written").containsExactly("PROFILE_CIONE");
        assertThat(cione.conditionGuards()).isEmpty();
    }

    @Test
    void skipsFilesWithoutTheAnnotationMarker() throws Exception {
        SourceScanResult<List<ExtractedFeatureUsage>> scan = new FeatureUsageScan().scan(new LocalArtemisSourceRepository(FIXTURE_PATH));

        assertThat(scan.facts()).extracting(ExtractedFeatureUsage::file).noneMatch(file -> file.endsWith("AlphaEnabled.java"))
                .noneMatch(file -> file.endsWith("AlphaConnectorConfiguration.java"));
    }

    @Test
    void warnsAboutAMalformedLabelAndKeepsThePlacement() throws Exception {
        SourceScanResult<List<ExtractedFeatureUsage>> scan = new FeatureUsageScan().scan(new LocalArtemisSourceRepository(MALFORMED_FIXTURE));

        assertThat(scan.facts()).singleElement().satisfies(usage -> {
            assertThat(usage.type()).isEqualTo("OmegaResource");
            assertThat(usage.classLabel()).isEqualTo("Omega Items");
        });
        assertThat(scan.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_FEATURE_USAGE_LABEL_MALFORMED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.subject()).isEqualTo("src/main/java/de/tum/cit/aet/artemis/omega/web/OmegaResource.java:11");
            assertThat(item.message()).contains("Omega Items").contains("kept");
        });
        assertThat(scan.wholeScannerFailed()).isFalse();
    }

    @Test
    void recordsProfileLiteralsMultipleGuardsAndMethodOnlyPlacements() throws Exception {
        writeSource("de/tum/cit/aet/artemis/omega/web/OmegaResource.java", """
                package de.tum.cit.aet.artemis.omega.web;

                import org.springframework.context.annotation.Conditional;
                import org.springframework.context.annotation.Profile;

                @Profile({ "scheduling", PROFILE_CORE, Constants.PROFILE_OMEGA })
                @Conditional({ OmegaEnabled.class, de.tum.cit.aet.artemis.core.config.SharedEnabled.class, NotACondition.class })
                public class OmegaResource {

                    @FeatureUsage("omega/items")
                    public String items() {
                        return "items";
                    }

                    @FeatureUsage(value = "omega/exports")
                    public String exports() {
                        return "exports";
                    }

                    public String unlabelled() {
                        return "none";
                    }
                }
                """);

        SourceScanResult<List<ExtractedFeatureUsage>> scan = new FeatureUsageScan().scan(new LocalArtemisSourceRepository(temporaryDirectory));

        assertThat(scan.diagnostics()).isEmpty();
        assertThat(scan.facts()).singleElement().satisfies(usage -> {
            assertThat(usage.classLabel()).isNull();
            assertThat(usage.methodLabels()).extracting(MethodLabel::method).containsExactly("exports", "items");
            assertThat(usage.effectiveLabels()).containsExactly("omega/exports", "omega/items");
            assertThat(usage.profileGuards()).containsExactly("PROFILE_CORE", "PROFILE_OMEGA", "scheduling");
            assertThat(usage.conditionGuards()).as("only *Enabled condition classes are guards").containsExactly("OmegaEnabled", "SharedEnabled");
            assertThat(usage.module()).isEqualTo("omega");
        });
    }

    @Test
    void reportsAnUnparseableFileAndKeepsScanningSiblings() throws Exception {
        writeSource("de/tum/cit/aet/artemis/omega/web/Broken.java", """
                package de.tum.cit.aet.artemis.omega.web;

                @FeatureUsage("omega/broken")
                public class Broken {
                """);
        writeSource("de/tum/cit/aet/artemis/omega/web/Working.java", """
                package de.tum.cit.aet.artemis.omega.web;

                @FeatureUsage("omega/working")
                public class Working {
                }
                """);

        SourceScanResult<List<ExtractedFeatureUsage>> scan = new FeatureUsageScan().scan(new LocalArtemisSourceRepository(temporaryDirectory));

        assertThat(scan.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.subject()).endsWith("Broken.java");
        });
        assertThat(scan.facts()).singleElement().satisfies(usage -> assertThat(usage.type()).isEqualTo("Working"));
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

    private ExtractedFeatureUsage usageOf(List<ExtractedFeatureUsage> usages, String type) {
        return usages.stream().filter(usage -> usage.type().equals(type)).findFirst().orElseThrow();
    }
}
