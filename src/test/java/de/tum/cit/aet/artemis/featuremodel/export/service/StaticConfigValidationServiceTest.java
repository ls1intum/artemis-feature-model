package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigFinding;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigValidationReport;
import tools.jackson.databind.ObjectMapper;

class StaticConfigValidationServiceTest {

    private DefaultResourceLoader resourceLoader;

    private ObjectMapper objectMapper;

    private StaticConfigValidationService service;

    @BeforeEach
    void setUp() {
        resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        service = new StaticConfigValidationService(resourceLoader, objectMapper);
    }

    @Test
    void passesAGeneratedStyleOverlay() {
        String overlay = """
                artemis:
                  iris:
                    enabled: true
                    url: https://pyris.example.com
                    secret-token: ${ARTEMIS_IRIS_SECRET_TOKEN}
                """;

        StaticConfigValidationReport report = service.validate(overlay);

        assertThat(report.overallStatus()).isEqualTo("PASS");
        assertThat(report.checkedEntryCount()).isEqualTo(3);
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void passesAnEmptyOverlay() {
        StaticConfigValidationReport report = service.validate("");

        assertThat(report.overallStatus()).isEqualTo("PASS");
        assertThat(report.checkedEntryCount()).isZero();
    }

    @Test
    void reportsAnUnknownKeyAsAFinding() {
        String overlay = """
                artemis:
                  unknown-feature:
                    enabled: true
                """;

        StaticConfigValidationReport report = service.validate(overlay);

        assertThat(report.overallStatus()).isEqualTo("FAIL");
        assertThat(report.findings()).hasSize(1);
        StaticConfigFinding finding = report.findings().get(0);
        assertThat(finding.path()).isEqualTo("artemis.unknown-feature.enabled");
        assertThat(finding.issue()).isEqualTo(StaticConfigFinding.ISSUE_UNKNOWN_KEY);
    }

    @Test
    void reportsATypeMismatchForANonBooleanToggle() {
        String overlay = """
                artemis:
                  iris:
                    enabled: "true"
                """;

        StaticConfigValidationReport report = service.validate(overlay);

        assertThat(report.overallStatus()).isEqualTo("FAIL");
        assertThat(report.findings()).hasSize(1);
        StaticConfigFinding finding = report.findings().get(0);
        assertThat(finding.path()).isEqualTo("artemis.iris.enabled");
        assertThat(finding.issue()).isEqualTo(StaticConfigFinding.ISSUE_TYPE_MISMATCH);
        assertThat(finding.detail()).contains("boolean").contains("string");
    }

    @Test
    void reportsATypeMismatchForAMalformedUrl() {
        String overlay = """
                artemis:
                  athena:
                    url: not-a-url
                """;

        StaticConfigValidationReport report = service.validate(overlay);

        assertThat(report.overallStatus()).isEqualTo("FAIL");
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).issue()).isEqualTo(StaticConfigFinding.ISSUE_TYPE_MISMATCH);
    }

    @Test
    void acceptsAnEnvironmentPlaceholderForAnyType() {
        String overlay = """
                artemis:
                  athena:
                    url: ${ARTEMIS_ATHENA_URL}
                  hyperion:
                    enabled: ${ARTEMIS_HYPERION_ENABLED}
                """;

        StaticConfigValidationReport report = service.validate(overlay);

        assertThat(report.overallStatus()).isEqualTo("PASS");
        assertThat(report.checkedEntryCount()).isEqualTo(2);
    }

    @Test
    void rejectsADocumentThatIsNotAMapping() {
        assertThatThrownBy(() -> service.validate("just a scalar")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Drift guard: every artifact mapping path in the active feature model must be present in the catalog. When a new
     * mapping is added to the model, this test fails until the new key is verified against Artemis and added to the
     * catalog resource.
     */
    @Test
    void catalogCoversEveryArtifactMappingPathInTheActiveModel() throws IOException {
        FeatureModel model = new JsonFeatureModelStore(resourceLoader, objectMapper).loadActiveModel();
        List<String> mappingPaths = model.features().stream().flatMap(feature -> feature.artifactMappings().stream()).map(mapping -> mapping.path()).distinct()
                .toList();

        ArtemisConfigKeyCatalog catalog = loadCatalog();
        List<String> catalogKeys = catalog.keys().stream().map(ArtemisConfigKeyCatalog.CatalogKey::key).toList();

        assertThat(mappingPaths).isNotEmpty();
        assertThat(catalogKeys).containsAll(mappingPaths);
    }

    @Test
    void catalogDeclaresOnlySupportedTypes() throws IOException {
        ArtemisConfigKeyCatalog catalog = loadCatalog();

        assertThat(catalog.keys()).isNotEmpty();
        assertThat(catalog.keys()).allSatisfy(key -> assertThat(key.type()).isIn(ArtemisConfigKeyCatalog.TYPE_BOOLEAN, ArtemisConfigKeyCatalog.TYPE_STRING,
                ArtemisConfigKeyCatalog.TYPE_URL));
    }

    private ArtemisConfigKeyCatalog loadCatalog() throws IOException {
        try (InputStream inputStream = resourceLoader.getResource(StaticConfigValidationService.CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(inputStream, ArtemisConfigKeyCatalog.class);
        }
    }
}
