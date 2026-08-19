package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.scan.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.WorkflowStageService;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in smoke run of the staged pipeline against a real local Artemis checkout. Enabled only when the
 * {@code artemisPath} system property is set, for example via {@code ./gradlew test -PartemisPath=/path/to/Artemis};
 * skipped silently otherwise.
 */
@EnabledIfSystemProperty(named = "artemisPath", matches = ".+")
class RealArtemisCheckoutSmokeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    private Path outputRoot;

    @Test
    void runsEveryStageAgainstTheRealCheckout() throws Exception {
        FeatureExtractionInputs inputs = inputs();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(inputs.requireArtemisCheckout());

        ScanStageService.Summary scan = new ScanStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new);
        ModelStageService.Summary model = new ModelStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new);
        WorkflowStageService.Summary workflow = new WorkflowStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new);
        PackageStageService.Summary packaged = new PackageStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new);

        assertThat(scan.candidateCount()).isGreaterThanOrEqualTo(50);
        assertThat(scan.relationCandidateCount()).isPositive();
        assertThat(model.modelIntegrityValid()).isTrue();
        assertThat(workflow.workflowIntegrityValid()).isTrue();
        assertThat(packaged.snapshotDirectory()).isNotNull();

        ExtractionReport report = objectMapper.readValue(Files.readAllBytes(packaged.reportDirectory().resolve("extraction-report.json")),
                ExtractionReport.class);
        assertThat(reportCodes(report)).doesNotContain(ReportItem.CODE_EXTRACTOR_ERROR);
        assertThat(report.items()).noneSatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_CURATED_ANCHOR_MISSING);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        assertThat(reportCodes(report)).as("every non-curated candidate carries an explicit manifest exclusion")
                .doesNotContain(ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL);
        assertThat(report.curation().undeclaredCandidateIds()).isEmpty();
        assertThat(report.curation().stateCounts()).containsEntry("undeclared", 0);
        assertThat(ExtractionArtifactLayout.forCommit(outputRoot, source.commit()).snapshotDirectory()).isDirectory();
    }

    /**
     * Resolves the bundled repository inputs against a temporary output root.
     *
     * @return command inputs for the smoke run.
     */
    private FeatureExtractionInputs inputs() {
        return new FeatureExtractionInputs(Path.of(System.getProperty("artemisPath")),
                Path.of("src/main/resources/feature-model/extraction/artemis-feature-manifest.yml"),
                Path.of("src/main/resources/feature-model/guided-workflow.json"),
                Path.of("src/main/resources/deployment-profiles/default-artemis-profile.json"),
                Path.of("delivery/artemis-runtime-image.json"), outputRoot);
    }

    /**
     * Collects the distinct diagnostic codes of the consolidated report.
     *
     * @param report consolidated extraction report.
     * @return report codes present in the run.
     */
    private List<String> reportCodes(ExtractionReport report) {
        return report.items().stream().map(ReportItem::code).distinct().toList();
    }
}
