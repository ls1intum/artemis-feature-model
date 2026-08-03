package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import tools.jackson.databind.ObjectMapper;

/** Covers catalog regeneration from overlay mappings, value type derivation, and the diff against the curated catalog. */
class GeneratedCatalogAssemblerTest {

    private final GeneratedCatalogAssembler assembler = new GeneratedCatalogAssembler();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void regeneratesCatalogFromOverlayMappingsWithDerivedTypes() {
        GeneratedCatalogAssembler.Result result = assembler.assemble(generatedModel(), yamlScan(), "0123456789abcdef");

        assertThat(result.catalog().verifiedAgainstArtemisCommit()).isEqualTo("0123456789abcdef");
        assertThat(result.catalog().keys()).extracting(ArtemisConfigKeyCatalog.CatalogKey::key).containsExactly("artemis.alpha.enabled", "artemis.alpha.secret",
                "artemis.alpha.url");
        assertThat(result.catalog().keys()).extracting(ArtemisConfigKeyCatalog.CatalogKey::type).containsExactly("boolean", "string", "url");
        // The non-overlay .env mapping never enters the catalog; the unobserved secret key is reported as info.
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT);
            assertThat(item.subject()).isEqualTo("artemis.alpha.secret");
        });
    }

    @Test
    void diffsRegeneratedCatalogAgainstCuratedCatalog() {
        ArtemisConfigKeyCatalog curated = new ArtemisConfigKeyCatalog("1.0.0", "oldpin1234", "curated", List.of(
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.alpha.enabled", "boolean"), new ArtemisConfigKeyCatalog.CatalogKey("artemis.alpha.url", "string"),
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.gone.enabled", "boolean")));
        ArtemisConfigKeyCatalog generated = assembler.assemble(generatedModel(), yamlScan(), "0123456789abcdef").catalog();

        ModelDiffReport.CatalogDiff diff = assembler.diff(curated, generated);

        assertThat(diff.curatedVerifiedAgainstArtemisCommit()).isEqualTo("oldpin1234");
        assertThat(diff.generatedVerifiedAgainstArtemisCommit()).isEqualTo("0123456789abcdef");
        assertThat(diff.addedKeys()).containsExactly("artemis.alpha.secret");
        assertThat(diff.removedKeys()).containsExactly("artemis.gone.enabled");
        assertThat(diff.typeChanges()).singleElement().satisfies(change -> {
            assertThat(change.key()).isEqualTo("artemis.alpha.url");
            assertThat(change.curatedType()).isEqualTo("string");
            assertThat(change.generatedType()).isEqualTo("url");
        });
    }

    private FeatureModel generatedModel() {
        List<ArtifactMapping> mappings = List.of(
                new ArtifactMapping("application-feature-model.yml", "artemis.alpha.enabled", objectMapper.valueToTree(Boolean.TRUE),
                        objectMapper.valueToTree(Boolean.FALSE), null, null, null),
                new ArtifactMapping("application-feature-model.yml", "artemis.alpha.url", null, null, "artemis.alpha.url", true, null),
                new ArtifactMapping("application-feature-model.yml", "artemis.alpha.secret", null, null, "artemis.alpha.secret", true, true),
                new ArtifactMapping(".env", "SPRING_PROFILES_ACTIVE", objectMapper.valueToTree("alpha"), null, null, null, null));
        FeatureNode alpha = new FeatureNode("alpha", "Alpha", "module", true, null, "enabled", null, null, null, null, null, mappings, null);
        return new FeatureModel(new ModelMetadata("generated", "Generated", "0.0.1"), List.of(alpha), List.of(), List.of());
    }

    private ExtractedConfigurationDefaults yamlScan() {
        return new ExtractedConfigurationDefaults(Map.of(
                "artemis.alpha.enabled", List.of(new ExtractedConfigurationDefault("src/main/resources/config/application-core.yml", 2, Boolean.TRUE)),
                "artemis.alpha.url", List.of(new ExtractedConfigurationDefault("src/main/resources/config/application-core.yml", 3, "http://localhost:5100"))),
                List.of());
    }
}
