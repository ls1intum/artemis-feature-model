package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
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
     * Drift guard: every overlay-target mapping path in the active feature model must be present in the catalog. When a
     * new overlay mapping is added to the model, this test fails until the new key is verified against Artemis and
     * added to the catalog resource. Structural mappings for other targets have separate consumers and are excluded.
     */
    @Test
    void catalogCoversEveryArtifactMappingPathInTheActiveModel() throws IOException {
        FeatureModel model = new JsonFeatureModelStore(resourceLoader, objectMapper).loadActiveModel();
        List<String> mappingPaths = overlayMappingPaths(model);

        ArtemisConfigKeyCatalog catalog = loadCatalog();
        List<String> catalogKeys = catalog.keys().stream().map(ArtemisConfigKeyCatalog.CatalogKey::key).toList();

        assertThat(mappingPaths).isNotEmpty();
        assertThat(catalogKeys).containsAll(mappingPaths);
    }

    @Test
    void catalogDriftGuardIgnoresStructuralMappingsOutsideTheOverlay() {
        FeatureModel model = modelWithMapping(".env", "SPRING_PROFILES_ACTIVE");

        assertThat(overlayMappingPaths(model)).isEmpty();
    }

    @Test
    void catalogDriftGuardKeepsOverlayMappingsInScope() {
        FeatureModel model = modelWithMapping(ArtifactMappingResolver.OVERLAY_TARGET, "artemis.synthetic.enabled");

        assertThat(overlayMappingPaths(model)).containsExactly("artemis.synthetic.enabled");
    }

    @Test
    void catalogDeclaresOnlySupportedTypes() throws IOException {
        ArtemisConfigKeyCatalog catalog = loadCatalog();

        assertThat(catalog.keys()).isNotEmpty();
        assertThat(catalog.keys()).allSatisfy(key -> assertThat(key.type()).isIn(ArtemisConfigKeyCatalog.TYPE_BOOLEAN, ArtemisConfigKeyCatalog.TYPE_STRING,
                ArtemisConfigKeyCatalog.TYPE_URL));
    }

    /**
     * The curated classpath catalog stays the default; a maintainer may explicitly point the service at a regenerated
     * catalog produced by the extraction pipeline through the catalog-location property.
     */
    @Test
    void loadsExplicitlySelectedGeneratedCatalog(@TempDir Path tempDir) throws IOException {
        Path generatedCatalog = tempDir.resolve("generated-config-key-catalog.json");
        Files.writeString(generatedCatalog, """
                {
                  "catalogVersion": "0.1.0+testcommit",
                  "verifiedAgainstArtemisCommit": "testcommit",
                  "source": "generated",
                  "keys": [ { "key": "artemis.generated.enabled", "type": "boolean" } ]
                }
                """);

        StaticConfigValidationService generatedCatalogService = new StaticConfigValidationService(resourceLoader, objectMapper,
                generatedCatalog.toUri().toString());
        StaticConfigValidationReport report = generatedCatalogService.validate("artemis:\n  generated:\n    enabled: true\n");

        assertThat(report.overallStatus()).isEqualTo(StaticConfigValidationReport.STATUS_PASS);
        assertThat(report.catalogVersion()).isEqualTo("0.1.0+testcommit");
        assertThat(generatedCatalogService.validate("artemis:\n  iris:\n    enabled: true\n").findings())
                .singleElement().satisfies(finding -> assertThat(finding.issue()).isEqualTo(StaticConfigFinding.ISSUE_UNKNOWN_KEY));
    }

    private ArtemisConfigKeyCatalog loadCatalog() throws IOException {
        try (InputStream inputStream = resourceLoader.getResource(StaticConfigValidationService.CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(inputStream, ArtemisConfigKeyCatalog.class);
        }
    }

    private List<String> overlayMappingPaths(FeatureModel model) {
        Set<String> paths = new LinkedHashSet<>();
        for (FeatureNode feature : model.features()) {
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (ArtifactMappingResolver.OVERLAY_TARGET.equals(mapping.target())) {
                    paths.add(mapping.path());
                }
            }
        }
        return List.copyOf(paths);
    }

    private FeatureModel modelWithMapping(String target, String path) {
        FeatureModel base = TestFeatureModels.baseModel();
        List<FeatureNode> features = new ArrayList<>();
        for (FeatureNode feature : base.features()) {
            features.add(addMappingToProgramming(feature, target, path));
        }
        return new FeatureModel(base.model(), features, base.relations(), base.constraints());
    }

    private FeatureNode addMappingToProgramming(FeatureNode feature, String target, String path) {
        if (!"programming".equals(feature.id())) {
            return feature;
        }
        ArtifactMapping mapping = new ArtifactMapping(target, path, ArtifactMappingSource.SELECTION, objectMapper.valueToTree(true), null, false);
        return new FeatureNode(feature.id(), feature.name(), feature.kind(), feature.selectable(), feature.description(), feature.defaultState(), feature.source(),
                feature.category(), feature.visibleTo(), feature.configurableBy(), feature.requiresCapabilities(), List.of(mapping), feature.extraction());
    }
}
