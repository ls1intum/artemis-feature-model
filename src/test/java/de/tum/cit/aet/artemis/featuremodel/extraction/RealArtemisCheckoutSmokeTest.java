package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureManifestLoader;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in smoke run against a real local Artemis checkout. Enabled only when the {@code artemisPath} system property is
 * set, for example via {@code ./gradlew test -PartemisPath=/path/to/Artemis}; skipped silently otherwise.
 */
class RealArtemisCheckoutSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "artemisPath", matches = ".+")
    void scansRealCheckoutAndRediscoversKnownModelGaps() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModel curatedModel = new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper).loadActiveModel();
        ArtemisConfigKeyCatalog catalog = loadCatalog(objectMapper);
        FeatureScopeManifest manifest = loadManifest();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(Path.of(System.getProperty("artemisPath")));

        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, curatedModel, catalog, manifest);

        assertThat(outcome.candidates().size()).isGreaterThanOrEqualTo(50);
        assertThat(outcome.relationCandidates()).isNotEmpty();
        assertThat(outcome.report().items()).noneSatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR));
        assertThat(outcome.report().items()).noneSatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_CURATED_ANCHOR_MISSING);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        List<String> newCandidateSubjects = outcome.report().items().stream().filter(item -> ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL.equals(item.code()))
                .map(ReportItem::subject).toList();
        assertThat(newCandidateSubjects).as("every non-curated candidate carries an explicit manifest exclusion").isEmpty();
        assertThat(outcome.report().curation().pendingCandidateIds()).isEmpty();
        assertThat(outcome.report().curation().stateCounts()).containsEntry("include", 20).containsEntry("exclude", 52).containsEntry("pending", 0);
    }

    /**
     * Loads the classpath config key catalog.
     *
     * @param objectMapper Jackson mapper.
     * @return parsed catalog.
     * @throws Exception if the catalog cannot be read.
     */
    private ArtemisConfigKeyCatalog loadCatalog(ObjectMapper objectMapper) throws Exception {
        try (InputStream inputStream = new DefaultResourceLoader().getResource("classpath:feature-model/artemis-config-key-catalog.json").getInputStream()) {
            return objectMapper.readValue(inputStream, ArtemisConfigKeyCatalog.class);
        }
    }

    /**
     * Loads the bundled scope manifest.
     *
     * @return parsed feature scope manifest.
     * @throws Exception if the manifest cannot be read.
     */
    private FeatureScopeManifest loadManifest() throws Exception {
        try (InputStream inputStream = new DefaultResourceLoader().getResource("classpath:feature-model/extraction/artemis-feature-manifest.yml").getInputStream()) {
            return new FeatureManifestLoader().load(inputStream, "bundled feature manifest");
        }
    }
}
