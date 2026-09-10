package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteAnsibleEmissionPlan;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests of the pure remote-ansible emission-plan layer: one test per emission semantics, including the rendered
 * null-override assertion, the environment-lookup rendering, membership wiring for both database choices and the iris
 * on/off states, and the fail-closed behavior for unsupported and unclassified features.
 */
class RemoteAnsibleEmissionPlannerTest {

    private static final List<String> MINIMAL_SELECTION = List.of("lecture", "tutorialgroup", "course-workflow", "communication", "exercise-common",
            "programming", "quiz", "text", "modeling", "file-upload", "exam", "plagiarism", "athena", "atlas", "iris", "hyperion", "lti", "theia", "apollon",
            "sharing", "mysql", "integrated-code-lifecycle", "localvc");

    private AnsibleBindingCatalog catalog;

    private FeatureModel model;

    private RemoteAnsibleEmissionPlanner planner;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        catalog = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper).catalog();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        model = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), new FeatureModelTreeService()).loadActiveModel();
        planner = new RemoteAnsibleEmissionPlanner(catalog);
    }

    @Test
    void baselineEntriesAreEmittedVerbatimIntoTheCommonConfig() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        String commonConfig = fileContent(plan, "inventory/group_vars/artemistests_common_config.yml");
        assertThat(commonConfig).startsWith("---\nnode_id: 1\nis_testserver: true\n");
        assertThat(commonConfig).contains("\nuse_docker: true\n");
        assertThat(commonConfig).contains("artemis_rate_limit:\n  account_management_rpm: 5\n  authentication_rpm: 30");
    }

    @Test
    void nullOverrideRendersTheExplicitNullUnconditionally() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        String commonConfig = fileContent(plan, "inventory/group_vars/artemistests_common_config.yml");
        assertThat(commonConfig).endsWith("\n\npush_notification_relay:");
    }

    @Test
    void identityValuesAreRenderedAsEnvironmentLookups() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(fileContent(plan, "inventory/group_vars/artemislocal/main.yml"))
                .isEqualTo("---\nvar_testserver_name: \"{{ lookup('ansible.builtin.env', 'TESTSERVER_NAME') }}\"\n"
                        + "var_server_hostname: \"{{ lookup('ansible.builtin.env', 'SERVER_HOSTNAME') }}\"");
        assertThat(fileContent(plan, "inventory/group_vars/artemistests_common_config.yml"))
                .contains("artemis_email: \"{{ lookup('ansible.builtin.env', 'ARTEMIS_EMAIL_TEST') }}\"")
                .contains("artemis_operator_name: \"{{ lookup('ansible.builtin.env', 'ARTEMIS_OPERATOR_NAME') }}\"")
                .contains("proxy_ssl_certificate_key_path: \"{{ lookup('ansible.builtin.env', 'PROXY_SSL_CERTIFICATE_KEY_PATH') }}\"");
    }

    @Test
    void secretsAreRenderedAsEnvironmentLookups() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(fileContent(plan, "inventory/group_vars/artemislocal/secrets.yml"))
                .contains("artemis_database_password: \"{{ lookup('ansible.builtin.env', 'ARTEMIS_DATABASE_PASSWORD') }}\"")
                .contains("artemis_internal_admin_password: \"{{ lookup('ansible.builtin.env', 'ARTEMIS_INTERNAL_ADMIN_PASSWORD') }}\"")
                .contains("artemis_jhipster_jwt: \"{{ lookup('ansible.builtin.env', 'ARTEMIS_JHIPSTER_JWT') }}\"");
    }

    @Test
    void hostsTargetGroupSectionIsEmpty() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        String hosts = fileContent(plan, RemoteAnsibleEmissionPlanner.HOSTS_FILE);
        assertThat(hosts).startsWith("[artemislocal]\n\n[artemistests:children]\nartemislocal\n");
        assertThat(hosts.lines().filter(line -> !line.isEmpty() && !line.startsWith("[")))
                .as("every non-section line wires the target group").allMatch("artemislocal"::equals);
    }

    @Test
    void presenceGatedFeatureEmitsBlockAndMembershipOnlyWhenSelected() {
        RemoteAnsibleEmissionPlan withIris = planner.plan(model, fullSelection(), labEnvironment());
        RemoteAnsibleEmissionPlan withoutIris = planner.plan(model, selectionWithout("iris"), labEnvironment());

        assertThat(filePaths(withIris)).contains("inventory/group_vars/artemistests_iris.yml");
        assertThat(fileContent(withIris, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_iris:children]\nartemislocal");
        assertThat(fileContent(withIris, "inventory/group_vars/artemistests_iris.yml"))
                .contains("iris:").contains("url: \"{{ lookup('ansible.builtin.env', 'IRIS_URL') }}\"");
        assertThat(filePaths(withoutIris)).doesNotContain("inventory/group_vars/artemistests_iris.yml");
        assertThat(fileContent(withoutIris, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).doesNotContain("artemistests_iris");
    }

    @Test
    void membershipFollowsTheSelectedDatabaseChoice() {
        RemoteAnsibleEmissionPlan mysqlPlan = planner.plan(model, fullSelection(), labEnvironment());
        Set<String> postgresSelection = new LinkedHashSet<>(fullSelection());
        postgresSelection.remove("mysql");
        postgresSelection.add("postgresql");
        RemoteAnsibleEmissionPlan postgresPlan = planner.plan(model, postgresSelection, labEnvironment());

        assertThat(fileContent(mysqlPlan, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_mysql:children]\nartemislocal")
                .doesNotContain("artemistests_postgres");
        assertThat(filePaths(mysqlPlan)).contains("inventory/group_vars/artemistests_mysql.yml").doesNotContain("inventory/group_vars/artemistests_postgres.yml");
        assertThat(fileContent(postgresPlan, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_postgres:children]\nartemislocal")
                .doesNotContain("artemistests_mysql");
        assertThat(fileContent(postgresPlan, "inventory/group_vars/artemistests_postgres.yml")).contains("artemis_database_type: postgresql");
    }

    @Test
    void deselectedModuleEmitsItsWithoutGroupFileAndMembership() {
        RemoteAnsibleEmissionPlan reduced = planner.plan(model, selectionWithout("exam"), labEnvironment());
        RemoteAnsibleEmissionPlan full = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(fileContent(reduced, "inventory/group_vars/artemistests_without_exam.yml")).isEqualTo("---\nartemis_modules:\n  exam: false");
        assertThat(fileContent(reduced, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_without_exam:children]\nartemislocal");
        assertThat(reduced.classifications()).anySatisfy(classification -> {
            assertThat(classification.featureId()).isEqualTo("exam");
            assertThat(classification.classification()).isEqualTo(AnsibleBindingCatalog.BINDING_BOUND);
            assertThat(classification.selected()).isFalse();
        });
        assertThat(filePaths(full)).noneMatch(path -> path.contains("artemistests_without_"));
        assertThat(fileContent(full, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).doesNotContain("artemistests_without_");
    }

    @Test
    void deselectedAtlasEmitsTheExplicitOffSwitchAndSelectedAtlasEmitsNothing() {
        RemoteAnsibleEmissionPlan withoutAtlas = planner.plan(model, selectionWithout("atlas"), labEnvironment());
        RemoteAnsibleEmissionPlan withAtlas = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(fileContent(withoutAtlas, "inventory/group_vars/artemistests_without_atlas.yml")).isEqualTo("---\natlas:\n  enabled: false");
        assertThat(fileContent(withoutAtlas, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_without_atlas:children]\nartemislocal");
        assertThat(filePaths(withAtlas)).noneMatch(path -> path.contains("atlas"));
        assertThat(fileContent(withAtlas, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).doesNotContain("atlas");
    }

    @Test
    void deselectedFileUploadMapsToTheUnhyphenatedArtemisModuleKey() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, selectionWithout("file-upload"), labEnvironment());

        assertThat(fileContent(plan, "inventory/group_vars/artemistests_without_fileupload.yml"))
                .isEqualTo("---\nartemis_modules:\n  fileupload: false");
        assertThat(fileContent(plan, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_without_fileupload:children]\nartemislocal");
    }

    @Test
    void jenkinsSelectionFailsClosedWithTheCatalogReason() {
        Set<String> jenkinsSelection = new LinkedHashSet<>(fullSelection());
        jenkinsSelection.remove("integrated-code-lifecycle");
        jenkinsSelection.add("jenkins");

        assertThatThrownBy(() -> planner.plan(model, jenkinsSelection, labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class)
                .hasMessageContaining("jenkins")
                .hasMessageContaining("no Jenkins service");
    }

    @Test
    void unclassifiedActiveModelFeatureIsRefusedWithTheCatalogIdentity() {
        List<FeatureNode> features = new ArrayList<>(model.features());
        features.add(new FeatureNode("brand-new-feature", "Brand New", "module", true, "A feature the catalog does not know.", "enabled", null));
        FeatureModel aheadModel = new FeatureModel(model.model(), features, model.relations(), model.constraints());

        assertThatThrownBy(() -> planner.plan(aheadModel, fullSelection(), labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class)
                .hasMessageContaining("brand-new-feature")
                .hasMessageContaining("Ansible binding catalog v" + catalog.catalogVersion())
                .hasMessageContaining(catalog.collectionPin());
    }

    @Test
    void envReferencesAreCollectedWithConsumerFileAndKind() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(plan.envReferences()).anySatisfy(reference -> {
            assertThat(reference.envVar()).isEqualTo("ARTEMIS_DATABASE_PASSWORD");
            assertThat(reference.consumer()).isEqualTo("artemis_database_password");
            assertThat(reference.file()).isEqualTo("inventory/group_vars/artemislocal/secrets.yml");
            assertThat(reference.kind()).isEqualTo(RemoteAnsibleEmissionPlan.ENV_KIND_SECRET);
        });
        assertThat(plan.envReferences()).anySatisfy(reference -> {
            assertThat(reference.envVar()).isEqualTo("TESTSERVER_NAME");
            assertThat(reference.consumer()).isEqualTo("var_testserver_name");
            assertThat(reference.file()).isEqualTo("inventory/group_vars/artemislocal/main.yml");
            assertThat(reference.kind()).isEqualTo(RemoteAnsibleEmissionPlan.ENV_KIND_IDENTITY);
        });
        assertThat(plan.envReferences()).anySatisfy(reference -> {
            assertThat(reference.envVar()).isEqualTo("IRIS_SECRET");
            assertThat(reference.consumer()).isEqualTo("iris.secret");
            assertThat(reference.file()).isEqualTo("inventory/group_vars/artemistests_iris.yml");
            assertThat(reference.kind()).isEqualTo(RemoteAnsibleEmissionPlan.ENV_KIND_SECRET);
        });
    }

    @Test
    void envReferencesFollowTheEmittedBindings() {
        RemoteAnsibleEmissionPlan withoutIris = planner.plan(model, selectionWithout("iris"), labEnvironment());

        assertThat(withoutIris.envReferences()).noneMatch(reference -> reference.envVar().startsWith("IRIS_"));
        assertThat(withoutIris.requiredEnvironmentVariables()).doesNotContain("IRIS_URL", "IRIS_SECRET");
    }

    @Test
    void requiredEnvironmentVariablesAreSortedAndDeduplicated() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        List<String> names = plan.requiredEnvironmentVariables();
        assertThat(names).isSorted().doesNotHaveDuplicates();
        assertThat(names).contains("ARTEMIS_DATABASE_PASSWORD", "SERVER_HOSTNAME", "AZURE_OPENAI_API_KEY", "SHARING_APIKEY");
    }

    @Test
    void noBuildAgentCredentialsAreEmittedOnALocalCiNode() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(plan.envReferences()).noneMatch(reference -> reference.consumer().contains("build_agent_git"));
        assertThat(fileContent(plan, "inventory/group_vars/artemistests_local_vc_ci.yml"))
                .doesNotContain("build_agent_git_credentials")
                .contains("build_agent_use_ssh: true");
    }

    @Test
    void noPlannedFileContainsAValueChannelOrPlaceholder() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        for (RemoteAnsibleEmissionPlan.PlannedFile file : plan.valuesFiles()) {
            assertThat(file.content()).as("file %s", file.path())
                    .doesNotContain("hashi_vault")
                    .doesNotContain("REPLACE_ME_")
                    .doesNotContain("{value}")
                    .doesNotContain("{vaultServerName}");
        }
    }

    @Test
    void targetNameCollidingWithAReservedGroupIsDisambiguated() {
        RemoteEnvironmentValues collidingTarget = RemoteEnvironmentValues.resolve("artemis-tests");
        RemoteEnvironmentValues collidingPrefix = RemoteEnvironmentValues.resolve("artemistests_mysql");

        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), collidingTarget);

        assertThat(collidingTarget.targetGroup()).isEqualTo("artemistests_target");
        assertThat(collidingPrefix.targetGroup()).isEqualTo("artemistests_mysql_target");
        String hosts = fileContent(plan, RemoteAnsibleEmissionPlanner.HOSTS_FILE);
        assertThat(hosts).startsWith("[artemistests_target]\n").contains("[artemistests:children]\nartemistests_target\n");
        assertThat(hosts).doesNotContain("[artemistests:children]\nartemistests\n");
    }

    @Test
    void absentTargetNameResolvesToTheDefaultGroupAndUnsafeNamesAreRejected() {
        assertThat(RemoteEnvironmentValues.defaultTarget().targetGroup()).isEqualTo("artemistarget");
        assertThat(RemoteEnvironmentValues.resolve("  ").targetGroup()).isEqualTo("artemistarget");
        assertThatThrownBy(() -> RemoteEnvironmentValues.resolve("bad name {{ lookup"))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("targetName");
    }

    @Test
    void unknownUnsupportedDirectionFailsClosedInEveryState() {
        AnsibleBindingCatalog.FeatureBinding misspelled = new AnsibleBindingCatalog.FeatureBinding(AnsibleBindingCatalog.BINDING_UNSUPPORTED, null, null, null,
                null, null, "deselcted", "artemis.exam.enabled has no collection variable", null, null);
        java.util.Map<String, AnsibleBindingCatalog.FeatureBinding> features = new java.util.HashMap<>(catalog.features());
        features.put("exam", misspelled);
        RemoteAnsibleEmissionPlanner misspelledPlanner = new RemoteAnsibleEmissionPlanner(new AnsibleBindingCatalog(catalog.catalogVersion(),
                catalog.collectionPin(), catalog.curationSource(), catalog.baseline(), catalog.environment(), catalog.secrets(), catalog.technical(), features));

        assertThatThrownBy(() -> misspelledPlanner.plan(model, fullSelection(), labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("exam").hasMessageContaining("unknown unsupported direction 'deselcted'");
        assertThatThrownBy(() -> misspelledPlanner.plan(model, selectionWithout("exam"), labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("unknown unsupported direction 'deselcted'");
    }

    @Test
    void coverageGateFollowsTheSelectableFlagNotTheNodeKind() {
        List<FeatureNode> features = new ArrayList<>(model.features());
        features.add(new FeatureNode("runtime-toggle-x", "Runtime Toggle", "runtime-toggle", true, "A selectable node of a kind the catalog never saw.",
                "enabled", null));
        FeatureModel aheadModel = new FeatureModel(model.model(), features, model.relations(), model.constraints());

        assertThatThrownBy(() -> planner.plan(aheadModel, fullSelection(), labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("runtime-toggle-x");
    }

    private Set<String> fullSelection() {
        return new LinkedHashSet<>(MINIMAL_SELECTION);
    }

    private Set<String> selectionWithout(String featureId) {
        Set<String> selection = fullSelection();
        selection.remove(featureId);
        return selection;
    }

    private RemoteEnvironmentValues labEnvironment() {
        return RemoteEnvironmentValues.resolve("artemis-local");
    }

    private String fileContent(RemoteAnsibleEmissionPlan plan, String path) {
        return plan.valuesFiles().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow(() ->
                new AssertionError("Missing planned file " + path)).content();
    }

    private List<String> filePaths(RemoteAnsibleEmissionPlan plan) {
        return plan.valuesFiles().stream().map(RemoteAnsibleEmissionPlan.PlannedFile::path).toList();
    }
}
