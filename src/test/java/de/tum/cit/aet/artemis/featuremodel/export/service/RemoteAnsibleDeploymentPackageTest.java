package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.dto.RemoteEnvironmentInput;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mode-composition tests of the remote-ansible deployment package: both first-target combinations compose with
 * correct membership and group files, a selected bound integration feature adds exactly its file and membership, and
 * inexpressible selections fail closed before any package bytes are produced.
 */
class RemoteAnsibleDeploymentPackageTest {

    private static final List<String> FULL_MYSQL_SELECTION = List.of("lecture", "tutorialgroup", "course-workflow", "communication", "exercise-common",
            "programming", "quiz", "text", "modeling", "file-upload", "exam", "plagiarism", "mysql", "integrated-code-lifecycle", "localvc");

    @TempDir
    Path dataRoot;

    private DeploymentPackageService service;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        service = new DeploymentPackageService(artifactGenerationService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(),
                new RemoteAnsibleValuesWriter(new AnsibleBindingCatalogLoader(resourceLoader, objectMapper)), new EnvExampleWriter(),
                new ArtemisRuntimeSourceResolver(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load(),
                        new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest")), objectMapper);
    }

    @Test
    void mysqlIntegratedCodeLifecycleSelectionComposesTheCompletePackage() {
        GeneratedArtifactPackage result = service.generate(remoteRequest(FULL_MYSQL_SELECTION));

        assertThat(result.files()).extracting("path").containsExactly("README.md", "requirements.yml", "ansible.cfg", "playbook.yml", "inventory/hosts",
                "inventory/group_vars/artemistarget/main.yml", "inventory/group_vars/artemistarget/secrets.yml",
                "inventory/group_vars/artemistests_common_config.yml", "inventory/group_vars/artemistests_mysql.yml",
                "inventory/group_vars/artemistests_local_vc_ci.yml", "inventory/group_vars/artemistests_without_atlas.yml", "preflight.sh",
                "metadata/package-manifest.json", "metadata/remote-readiness.json", "metadata/env-references.json", "metadata/selected-features.json");
        assertThat(content(result, "inventory/hosts")).contains("[artemistests_mysql:children]\nartemistarget")
                .contains("[artemistests_local_vc_ci:children]\nartemistarget").contains("[artemistests_without_atlas:children]\nartemistarget")
                .doesNotContain("artemistests_postgres");
        assertThat(content(result, "requirements.yml")).contains("version: fce6ad19a7ee58dbecc5632d5bb2b3f18f76886e");
        assertThat(content(result, "ansible.cfg")).contains("hash_behaviour = merge").contains("[ssh_connection]\npipelining = True");
    }

    @Test
    void postgresqlIntegratedCodeLifecycleSelectionComposesThePostgresVariant() {
        List<String> selection = replace(FULL_MYSQL_SELECTION, "mysql", "postgresql");

        GeneratedArtifactPackage result = service.generate(remoteRequest(selection));

        assertThat(result.files()).extracting("path").contains("inventory/group_vars/artemistests_postgres.yml")
                .doesNotContain("inventory/group_vars/artemistests_mysql.yml");
        assertThat(content(result, "inventory/group_vars/artemistests_postgres.yml")).contains("artemis_database_type: postgresql")
                .contains("artemis_database_host: \"artemis-postgres\"");
        assertThat(content(result, "inventory/hosts")).contains("[artemistests_postgres:children]\nartemistarget").doesNotContain("artemistests_mysql");
    }

    @Test
    void selectedIrisAddsExactlyItsGroupFileAndMembership() {
        List<String> withIris = withExtra(FULL_MYSQL_SELECTION, "iris");

        GeneratedArtifactPackage base = service.generate(remoteRequest(FULL_MYSQL_SELECTION));
        GeneratedArtifactPackage irisVariant = service.generate(remoteRequest(withIris));

        List<String> basePaths = base.files().stream().map(GeneratedArtifactFile::path).toList();
        List<String> irisPaths = irisVariant.files().stream().map(GeneratedArtifactFile::path).toList();
        List<String> addedPaths = new ArrayList<>(irisPaths);
        addedPaths.removeAll(basePaths);
        assertThat(addedPaths).containsExactly("inventory/group_vars/artemistests_iris.yml");
        assertThat(content(irisVariant, "inventory/hosts")).contains("[artemistests_iris:children]\nartemistarget");
        assertThat(content(irisVariant, "inventory/group_vars/artemistests_iris.yml"))
                .contains("url: \"{{ lookup('ansible.builtin.env', 'IRIS_URL') }}\"");
        assertThat(content(irisVariant, "preflight.sh")).contains("IRIS_URL").contains("IRIS_SECRET");
        assertThat(content(base, "preflight.sh")).doesNotContain("IRIS_URL");
    }

    @Test
    void moduleReductionComposesTheWithoutGroupFileAndMembership() {
        List<String> reduced = new ArrayList<>(FULL_MYSQL_SELECTION);
        reduced.remove("exam");

        GeneratedArtifactPackage result = service.generate(remoteRequest(reduced));

        assertThat(result.files()).extracting("path").contains("inventory/group_vars/artemistests_without_exam.yml");
        assertThat(content(result, "inventory/group_vars/artemistests_without_exam.yml")).isEqualTo("---\nartemis_modules:\n  exam: false");
        assertThat(content(result, "inventory/hosts")).contains("[artemistests_without_exam:children]\nartemistarget");
    }

    @Test
    void jenkinsSelectionFailsClosedWithTheCatalogReason() {
        List<String> jenkinsSelection = replace(FULL_MYSQL_SELECTION, "integrated-code-lifecycle", "jenkins");

        assertThatThrownBy(() -> service.generate(remoteRequest(jenkinsSelection)))
                .isInstanceOf(ArtifactGenerationException.class)
                .hasMessageContaining("jenkins")
                .hasMessageContaining("no Jenkins service");
    }

    @Test
    void manifestKeepsTheSharedRecordShapeAndRecordsTheRemoteMode() {
        GeneratedArtifactPackage result = service.generate(remoteRequest(FULL_MYSQL_SELECTION));

        DeploymentPackageManifest manifest = objectMapper.readValue(content(result, "metadata/package-manifest.json"), DeploymentPackageManifest.class);
        assertThat(manifest.packageType()).isEqualTo("remote-ansible-deployment-package");
        assertThat(manifest.packageVersion()).isEqualTo("1.0.0");
        assertThat(manifest.deploymentMode()).isEqualTo("remote-ansible");
        assertThat(manifest.supportedRuntimeModes()).isEmpty();
        assertThat(manifest.requiredEnvironmentVariables()).isEmpty();
        assertThat(manifest.database().type()).isEqualTo("mysql");
        assertThat(manifest.database().mode()).isEqualTo("ansible-managed");
        assertThat(manifest.ciProvider().type()).isEqualTo("integrated-code-lifecycle");
        assertThat(manifest.readiness().localRuntimeReady()).isFalse();
        assertThat(manifest.readiness().productionReady()).isFalse();
        assertThat(manifest.generatedFiles()).isEqualTo(result.files().stream().map(GeneratedArtifactFile::path).toList());
    }

    @Test
    void readinessRecordsLayersClassificationsAndDualAxisProvenance() {
        GeneratedArtifactPackage result = service.generate(remoteRequest(FULL_MYSQL_SELECTION));

        JsonNode readiness = objectMapper.readTree(content(result, "metadata/remote-readiness.json"));
        assertThat(readiness.get("selectionValidated").asString()).isEqualTo("pass");
        assertThat(readiness.get("valuesGenerated").asString()).isEqualTo("pass");
        assertThat(readiness.get("secretsAsReferences").asString()).isEqualTo("pass");
        assertThat(readiness.get("syntaxValidated").asString()).isEqualTo("pending");
        assertThat(readiness.get("bindingsResolved").isArray()).isTrue();
        List<String> requiredNames = new ArrayList<>();
        for (JsonNode name : readiness.get("requiredEnvironmentVariables")) {
            requiredNames.add(name.asString());
        }
        assertThat(requiredNames).contains("ARTEMIS_DATABASE_PASSWORD", "SERVER_HOSTNAME", "ARTEMIS_EMAIL_TEST");
        assertThat(readiness.get("bindingCatalog").get("catalogVersion").asInt()).isEqualTo(2);
        assertThat(readiness.get("bindingCatalog").get("collectionPin").asString()).isEqualTo("fce6ad19a7ee58dbecc5632d5bb2b3f18f76886e");
        assertThat(readiness.get("model").get("id").asString()).isNotEmpty();
    }

    @Test
    void noGeneratedByteContainsAValueChannelOrBakedEnvironmentValue() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(withExtra(FULL_MYSQL_SELECTION, "iris", "hyperion"), null, null,
                "remote-ansible", labEnvironment()));

        String secretsFile = content(result, "inventory/group_vars/artemislocal/secrets.yml");
        assertThat(secretsFile.lines().filter(line -> line.contains(":") && !line.startsWith("#") && !line.equals("---")))
                .allMatch(line -> line.contains("lookup('ansible.builtin.env'"));
        for (GeneratedArtifactFile file : result.files()) {
            assertThat(file.content()).as("file %s", file.path())
                    .doesNotContain("secrets.example")
                    .doesNotContain("hashi_vault")
                    .doesNotContain("REPLACE_ME_")
                    .doesNotContain("nip.io")
                    .doesNotContain("thesis.invalid")
                    .doesNotContain("Junting");
        }
    }

    @Test
    void targetNameOnlyChangesTheTargetGroupNaming() {
        GeneratedArtifactPackage defaultPackage = service.generate(remoteRequest(FULL_MYSQL_SELECTION));
        GeneratedArtifactPackage labPackage = service.generate(new ArtifactGenerationRequest(FULL_MYSQL_SELECTION, null, null, "remote-ansible",
                labEnvironment()));

        List<String> renamedDefaultPaths = defaultPackage.files().stream().map(GeneratedArtifactFile::path)
                .map(path -> path.replace("artemistarget", "artemislocal")).toList();
        assertThat(labPackage.files().stream().map(GeneratedArtifactFile::path).toList()).isEqualTo(renamedDefaultPaths);
        assertThat(content(labPackage, "inventory/group_vars/artemislocal/main.yml"))
                .isEqualTo(content(defaultPackage, "inventory/group_vars/artemistarget/main.yml"));
        assertThat(content(labPackage, "inventory/hosts")).startsWith("[artemislocal]\n\n");
        assertThat(content(defaultPackage, "inventory/hosts")).startsWith("[artemistarget]\n\n");
    }

    @Test
    void preflightEmbedsTheRequiredEnvironmentVariableGate() {
        GeneratedArtifactPackage result = service.generate(remoteRequest(FULL_MYSQL_SELECTION));

        String preflight = content(result, "preflight.sh");
        JsonNode readiness = objectMapper.readTree(content(result, "metadata/remote-readiness.json"));
        for (JsonNode name : readiness.get("requiredEnvironmentVariables")) {
            assertThat(preflight).contains("\n" + name.asString() + "\n");
        }
        assertThat(preflight).contains("is not set or is empty").contains("--syntax-check");
        assertThat(preflight.indexOf("required environment variables")).isLessThan(preflight.indexOf("--syntax-check"));
    }

    @Test
    void remoteEnvironmentOnNonRemoteModesIsRejected() {
        assertThatThrownBy(() -> service.generate(new ArtifactGenerationRequest(FULL_MYSQL_SELECTION, null, null, "local-docker", labEnvironment())))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("local-docker");
        assertThatThrownBy(() -> service.generate(new ArtifactGenerationRequest(FULL_MYSQL_SELECTION, null, null, "dev-ide", labEnvironment())))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("dev-ide");
        assertThatThrownBy(() -> service.generate(new ArtifactGenerationRequest(FULL_MYSQL_SELECTION, null, null, null, labEnvironment())))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("remote-ansible");
    }

    @Test
    void requirementsPinTheArtemisCollectionAndDeclareTheCollectionsItsRolesNeed() {
        GeneratedArtifactPackage result = service.generate(remoteRequest(FULL_MYSQL_SELECTION));

        String requirements = content(result, "requirements.yml");
        assertThat(requirements).contains("version: fce6ad19a7ee58dbecc5632d5bb2b3f18f76886e").contains("- name: ansible.posix")
                .contains("- name: community.crypto").contains("- name: community.general").doesNotContain("hashi_vault");
        assertThat(content(result, "README.md")).contains("lookup('ansible.builtin.env', …)").contains("env-references.json");
    }

    private RemoteEnvironmentInput labEnvironment() {
        return new RemoteEnvironmentInput("artemis-local");
    }

    private ArtifactGenerationRequest remoteRequest(List<String> selection) {
        return new ArtifactGenerationRequest(selection, null, null, "remote-ansible");
    }

    private List<String> withExtra(List<String> selection, String... extras) {
        List<String> extended = new ArrayList<>(selection);
        extended.addAll(List.of(extras));
        return extended;
    }

    private List<String> replace(List<String> selection, String from, String to) {
        List<String> replaced = new ArrayList<>(selection);
        replaced.remove(from);
        replaced.add(to);
        return replaced;
    }

    private String content(GeneratedArtifactPackage result, String path) {
        return result.files().stream().filter(file -> file.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing package file " + path)).content();
    }
}
