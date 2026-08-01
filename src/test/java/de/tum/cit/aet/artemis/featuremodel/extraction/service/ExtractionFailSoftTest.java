package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

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
}
