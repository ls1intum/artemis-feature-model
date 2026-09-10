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

        assertThat(catalog.catalogVersion()).isEqualTo(4);
        assertThat(catalog.collectionPin()).isEqualTo("13e50a20fea641a5a792e42541952a37cd7f1239");
        assertThat(catalog.curationSource()).contains("transformation-table.md");
    }

    @Test
    void catalogEnvironmentVariableNamesEqualTheProvisionedMappingExactly() {
        AnsibleBindingCatalog catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();

        Set<String> declaredNames = new HashSet<>();
        for (var file : List.of(catalog.files().targetMain(), catalog.files().targetSecrets(), catalog.files().commonConfig())) {
            for (var reference : file.envReferences()) {
                declaredNames.add(reference.envVar());
            }
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
    void unsupportedBindingWithoutReasonFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "no-op"
                      reason: "r"
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                features:
                  exam:
                    binding: "unsupported"
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("missing variable or reason");
    }

    @Test
    void unknownBindingClassificationFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "no-op"
                      reason: "r"
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                features:
                  iris:
                    binding: "maybe"
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown classification");
    }

    @Test
    void unknownUnsupportedDirectionFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "no-op"
                      reason: "r"
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                features:
                  exam:
                    binding: "unsupported"
                    unsupportedWhen: "deselcted"
                    missingVariable: "x"
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown direction 'deselcted'");
    }

    @Test
    void unknownBoundGatingFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "no-op"
                      reason: "r"
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                features:
                  exam:
                    binding: "bound"
                    gating: "deslected"
                    membership: "artemistests_without_exam"
                    content: |-
                      ---
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("unknown gating 'deslected'");
    }

    @Test
    void gatingOnATechnicalBindingFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "bound"
                      gating: "deselected"
                      membership: "artemistests_mysql"
                      content: |-
                        ---
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("must not declare a gating");
    }

    @Test
    void missingTechnicalAxesFailLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                features: {}
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("technical database and ciProvider");
    }

    @Test
    void duplicateGroupValuesFileFailsLoading() {
        String catalogYaml = """
                catalogVersion: 1
                collectionPin: "8977303c560a91be27214509dd07bf6170c97277"
                technical:
                  database:
                    mysql:
                      binding: "no-op"
                      reason: "r"
                  ciProvider:
                    icl:
                      binding: "no-op"
                      reason: "r"
                features:
                  iris:
                    binding: "bound"
                    membership: "artemistests_iris"
                    content: |-
                      ---
                  atlas:
                    binding: "bound"
                    membership: "artemistests_iris"
                    content: |-
                      ---
                files:
                  targetMain:
                    content: |-
                      ---
                  targetSecrets:
                    content: |-
                      ---
                  commonConfig:
                    content: |-
                      ---
                """;

        assertThatThrownBy(() -> loadCatalog(catalogYaml)).isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("declared by more than one bound binding");
    }

    @Test
    void malformedCatalogFailsLoading() {
        assertThatThrownBy(() -> loadCatalog("{ not json")).isInstanceOf(FeatureModelLoadException.class);
    }

    @Test
    void annotationsAreStrippedAndReferencesFollowDottedConsumers() throws IOException {
        String bundled = Files.readString(Path.of("src/main/resources/deployment-bindings/artemis-ansible-binding-catalog.yml"));
        AnsibleBindingCatalog catalog = loadCatalog(bundled);
        assertThat(catalog.files().targetSecrets().content()).doesNotContain("#:")
                .contains("# Secret values are never stored");
        assertThat(catalog.bindingFor("iris").envReferences()).containsExactly(
                new AnsibleBindingCatalog.EnvReference("IRIS_URL", "iris.url"),
                new AnsibleBindingCatalog.EnvReference("IRIS_SECRET", "iris.secret"));
        assertThat(catalog.bindingFor("hyperion").envReferences().getFirst().consumer()).isEqualTo("spring_ai.azure_openai.api_key");
    }

    @Test
    void unknownKeysAndMissingBlocksFailLoading() throws IOException {
        String bundled = Files.readString(Path.of("src/main/resources/deployment-bindings/artemis-ansible-binding-catalog.yml"));
        assertThatThrownBy(() -> loadCatalog(bundled.replace("gating:", "gatting:")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("Unknown key 'gatting'");
        assertThatThrownBy(() -> loadCatalog(bundled.replace("targetMain:", "targetTypo:")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("Unknown key");
        assertThatThrownBy(() -> loadCatalog(bundled.replaceFirst("(?s)  targetMain:.*?  targetSecrets:", "  targetSecrets:")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("Missing targetMain");
        assertThatThrownBy(() -> loadCatalog(bundled.replace("    content: |-", "    missing: |-")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("Unknown key");
    }

    @Test
    void malformedContentAndLowercaseLookupFailLoading() throws IOException {
        String bundled = Files.readString(Path.of("src/main/resources/deployment-bindings/artemis-ansible-binding-catalog.yml"));
        assertThatThrownBy(() -> loadCatalog(bundled.replace("var_testserver_name:", "bad: [\n      var_testserver_name:")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("targetMain");
        assertThatThrownBy(() -> loadCatalog(bundled.replace("TESTSERVER_NAME", "lowercase")))
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("uppercase environment-variable name");
    }

    private boolean isClassified(AnsibleBindingCatalog catalog, String featureId) {
        return catalog.features().containsKey(featureId) || catalog.technical().database().containsKey(featureId)
                || catalog.technical().ciProvider().containsKey(featureId);
    }

    private AnsibleBindingCatalog loadCatalog(String catalogYaml) {
        try {
            Path catalogFile = tempDir.resolve("catalog.yml");
            Files.writeString(catalogFile, catalogYaml);
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
