package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * The user-provisioned environment-variable names of
     * {@code devdocs/plan/deployment/ansible-remote/gitops/ansible-package-github-secrets-mapping.txt} (2026-08-29),
     * verbatim: the 7 identity values and the 12 secret-class values.
     */
    private static final List<String> PROVISIONED_ENV_VAR_NAMES = List.of(
            "TESTSERVER_NAME", "SERVER_HOSTNAME", "ARTEMIS_EMAIL_TEST", "ARTEMIS_OPERATOR_NAME", "ARTEMIS_OPERATOR_ADMIN_NAME",
            "PROXY_SSL_CERTIFICATE_PATH", "PROXY_SSL_CERTIFICATE_KEY_PATH",
            "ARTEMIS_DATABASE_PASSWORD", "ARTEMIS_INTERNAL_ADMIN_PASSWORD", "ARTEMIS_JHIPSTER_JWT",
            "ATHENA_URL", "ATHENA_SECRET", "AZURE_OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_DEPLOYMENT_NAME",
            "IRIS_URL", "IRIS_SECRET", "LTI_OAUTH_SECRET", "SHARING_APIKEY");

    @Test
    void bundledCatalogLoadsWithItsCollectionPinIdentity() {
        AnsibleBindingCatalog catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();

        assertThat(catalog.catalogVersion()).isEqualTo(2);
        assertThat(catalog.collectionPin()).isEqualTo("fce6ad19a7ee58dbecc5632d5bb2b3f18f76886e");
        assertThat(catalog.curationSource()).contains("transformation-table.md");
    }

    @Test
    void catalogEnvironmentVariableNamesEqualTheProvisionedMappingExactly() {
        AnsibleBindingCatalog catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();

        Set<String> declaredNames = new HashSet<>();
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            declaredNames.add(entry.envVar());
        }
        for (AnsibleBindingCatalog.SecretEntry entry : catalog.secrets()) {
            declaredNames.add(entry.envVar());
        }
        for (Map<String, AnsibleBindingCatalog.FeatureBinding> section : catalog.sections()) {
            for (AnsibleBindingCatalog.FeatureBinding binding : section.values()) {
                for (AnsibleBindingCatalog.EnvReference reference : binding.envReferences()) {
                    declaredNames.add(reference.envVar());
                }
            }
        }

        assertThat(declaredNames).as("catalog environment-variable names vs the provisioned mapping")
                .containsExactlyInAnyOrderElementsOf(PROVISIONED_ENV_VAR_NAMES);
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
    void environmentEntryWithoutUppercaseEnvVarFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "environment": [ { "var": "artemis_email", "envVar": "mail", "file": "common-config", "order": 1, "group": 1,
                    "lines": ["artemis_email: \\"{{ lookup('ansible.builtin.env', 'mail') }}\\""] } ],
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("uppercase environment-variable name");
    }

    @Test
    void environmentEntryNotRenderingItsLookupFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "environment": [ { "var": "artemis_email", "envVar": "ARTEMIS_EMAIL_TEST", "file": "common-config", "order": 1, "group": 1,
                    "lines": ["artemis_email: \\"someone@example.org\\""] } ],
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("does not render the environment lookup of 'ARTEMIS_EMAIL_TEST'");
    }

    @Test
    void secretEntryWithoutEnvVarFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "secrets": [ { "var": "artemis_database_password" } ],
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("uppercase environment-variable name");
    }

    @Test
    void boundBindingNotRenderingADeclaredEnvReferenceFailsLoading() {
        String catalogJson = """
                { "catalogVersion": 1, "collectionPin": "8977303c560a91be27214509dd07bf6170c97277",
                  "technical": { "database": { "mysql": { "binding": "no-op", "reason": "r" } }, "ciProvider": { "icl": { "binding": "no-op", "reason": "r" } } },
                  "features": { "iris": { "binding": "bound", "membership": "artemistests_iris", "groupVarsFile": "artemistests_iris.yml",
                    "lines": ["---", "iris:", "  url: \\"https://example.org\\""],
                    "envReferences": [ { "envVar": "IRIS_URL", "consumer": "iris.url" } ] } } }
                """;

        assertThatThrownBy(() -> loadCatalog(catalogJson)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("declares environment reference 'IRIS_URL' but does not render its lookup");
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
