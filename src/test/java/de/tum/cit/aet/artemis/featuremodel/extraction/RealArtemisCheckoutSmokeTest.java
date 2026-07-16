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
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;
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
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(Path.of(System.getProperty("artemisPath")));

        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, curatedModel, catalog);

        assertThat(outcome.candidates().size()).isGreaterThanOrEqualTo(50);
        assertThat(outcome.relationCandidates()).isNotEmpty();
        assertThat(outcome.report().items()).noneSatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR));
        assertThat(outcome.report().items()).noneSatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_CURATED_ANCHOR_MISSING);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        List<String> newCandidateSubjects = outcome.report().items().stream().filter(item -> ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL.equals(item.code()))
                .map(ReportItem::subject).toList();
        assertThat(newCandidateSubjects).contains("module:ldap", "module:saml2", "module:passkey", "module:passkey-admin", "module:atlasml", "module:weaviate",
                "module:tumlive");
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
}
