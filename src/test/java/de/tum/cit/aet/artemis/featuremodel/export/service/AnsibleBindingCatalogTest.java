package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards the curated Ansible binding catalog: the bundled catalog must load, carry its collection-pin identity, and
 * classify every selectable feature of the served model as bound, no-op, or unsupported. Broken catalogs must fail
 * loading instead of degrading silently.
 */
class AnsibleBindingCatalogTest {

    @TempDir
    Path tempDir;

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bundledCatalogLoadsWithItsCollectionPinIdentity() {
        AnsibleBindingCatalog catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();

        assertThat(catalog.catalogVersion()).isEqualTo(1);
        assertThat(catalog.collectionPin()).isEqualTo("fce6ad19a7ee58dbecc5632d5bb2b3f18f76886e");
        assertThat(catalog.curationSource()).contains("transformation-table.md");
    }

    @Test
    void everySelectableFeatureOfTheServedModelIsClassified() {
        AnsibleBindingCatalog catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();
        FeatureModel model = loadClasspathModel();

        List<String> unclassified = model.features().stream().filter(FeatureNode::selectable).map(FeatureNode::id)
                .filter(featureId -> !isClassified(catalog, featureId)).toList();

        assertThat(unclassified).as("selectable features without a binding classification").isEmpty();
    }

    @Test
    void unknownEmissionKindFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "baseline": [ { "var": "node_id", "emission": "sometimes", "order": 10, "group": 1, "lines": ["node_id: 1"] } ],
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown emission kind");
    }

    @Test
    void nullOverrideWithoutReasonFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "baseline": [ { "var": "push_notification_relay", "emission": "null-override", "order": 10, "group": 1, "lines": ["push_notification_relay:"] } ] }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("Null-override");
    }

    @Test
    void unsupportedBindingWithoutReasonFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": { "exam": { "binding": "unsupported" } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("missing variable or reason");
    }

    @Test
    void unknownBindingClassificationFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": { "iris": { "binding": "maybe" } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown classification");
    }

    @Test
    void unknownUnsupportedDirectionFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": { "exam": { "binding": "unsupported", "unsupportedWhen": "deselcted", "missingVariable": "x" } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown direction 'deselcted'");
    }

    @Test
    void unknownBoundGatingFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": { "exam": { "binding": "bound", "gating": "deslected", "membership": "artemistests_without_exam",
                    "groupVarsFile": "artemistests_without_exam.yml", "lines": ["---"] } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown gating 'deslected'");
    }

    @Test
    void gatingOnATechnicalBindingFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "bound", "gating": "deselected", "membership": "artemistests_mysql",
                    "groupVarsFile": "artemistests_mysql.yml", "lines": ["---"] } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("must not declare a gating");
    }

    @Test
    void unknownEnvironmentInputFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "environment": [ { "var": "artemis_email", "input": "mail", "file": "common-config", "order": 1, "group": 1, "lines": ["artemis_email: {value}"] } ],
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown input 'mail'");
    }

    @Test
    void missingTechnicalAxesFailLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277", "features": {} }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("technical database and ciProvider");
    }

    @Test
    void duplicateGroupValuesFileFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": {
                    "iris": { "binding": "bound", "membership": "artemistests_iris", "groupVarsFile": "artemistests_iris.yml", "lines": ["---"] },
                    "atlas": { "binding": "bound", "membership": "artemistests_atlas", "groupVarsFile": "artemistests_iris.yml", "lines": ["---"] } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("declared by more than one bound binding");
    }

    @Test
    void malformedCatalogFailsLoading() {
        assertThatThrownBy(() -> loadCatalog("{ not json")).isInstanceOf(FeatureModelLoadException.class);
    }

    private boolean isClassified(AnsibleBindingCatalog catalog, String featureId) {
        return catalog.features().containsKey(featureId) || catalog.technical().database().containsKey(featureId)
                || catalog.technical().ciProvider().containsKey(featureId);
    }

    private AnsibleBindingCatalog loadCatalog(String catalogJson) {
        try {
            Path catalogFile = tempDir.resolve("catalog.json");
            Files.writeString(catalogFile, catalogJson);
            return new AnsibleBindingCatalogLoader(resourceLoader, objectMapper, "file:" + catalogFile).catalog();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not write the test catalog.", e);
        }
    }

    private FeatureModel loadClasspathModel() {
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), new FeatureModelTreeService());
        return catalogService.loadActiveModel();
    }
}
