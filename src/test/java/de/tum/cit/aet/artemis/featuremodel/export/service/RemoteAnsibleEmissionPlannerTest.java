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
 * Unit tests of the pure remote-ansible emission-plan layer: one test per emission semantics, including the
 * five-state value-gated contract, the rendered null-override assertion, membership wiring for both database choices
 * and the iris on/off states, and the fail-closed behavior for unsupported and unclassified features.
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
    void presenceGatedFeatureEmitsBlockAndMembershipOnlyWhenSelected() {
        RemoteAnsibleEmissionPlan withIris = planner.plan(model, fullSelection(), labEnvironment());
        RemoteAnsibleEmissionPlan withoutIris = planner.plan(model, selectionWithout("iris"), labEnvironment());

        assertThat(filePaths(withIris)).contains("inventory/group_vars/artemistests_iris.yml");
        assertThat(fileContent(withIris, RemoteAnsibleEmissionPlanner.HOSTS_FILE)).contains("[artemistests_iris:children]\nartemislocal");
        assertThat(fileContent(withIris, "inventory/group_vars/artemistests_iris.yml"))
                .contains("iris:").contains("lookup('hashi_vault', 'kv/data/artemis/common/pyris-test')");
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
    void valueGatedBlockIsEmittedOnlyWhenComplete() {
        AnsibleBindingCatalog.FeatureBinding complete = valueGatedBinding(List.of(field("api_key", "    api_key: \"a\""), field("endpoint", "    endpoint: \"b\"")));
        AnsibleBindingCatalog.FeatureBinding undefinedFields = valueGatedBinding(null);
        AnsibleBindingCatalog.FeatureBinding nullField = valueGatedBinding(List.of(field("api_key", null)));
        AnsibleBindingCatalog.FeatureBinding emptyBlock = valueGatedBinding(List.of());
        AnsibleBindingCatalog.FeatureBinding partiallyEmpty = valueGatedBinding(List.of(field("api_key", "    api_key: \"a\""), field("endpoint", " ")));

        assertThat(planner.valueGatedBlockLines(complete)).containsExactly("---", "artemis_hyperion_enabled: true", "    api_key: \"a\"",
                "    endpoint: \"b\"", "    temperature: 1.0");
        assertThat(planner.valueGatedBlockLines(undefinedFields)).isEmpty();
        assertThat(planner.valueGatedBlockLines(nullField)).isEmpty();
        assertThat(planner.valueGatedBlockLines(emptyBlock)).isEmpty();
        assertThat(planner.valueGatedBlockLines(partiallyEmpty)).isEmpty();
    }

    @Test
    void moduleReductionSelectionFailsClosedNamingTheMissingVariable() {
        assertThatThrownBy(() -> planner.plan(model, selectionWithout("exam"), labEnvironment()))
                .isInstanceOf(ArtifactGenerationException.class)
                .hasMessageContaining("exam")
                .hasMessageContaining("artemis.exam.enabled has no collection variable at the pinned commit");
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
    void providedEnvironmentValuesAreRenderedAndPlaceholdersOtherwise() {
        RemoteAnsibleEmissionPlan labPlan = planner.plan(model, fullSelection(), labEnvironment());
        RemoteAnsibleEmissionPlan placeholderPlan = planner.plan(model, fullSelection(), RemoteEnvironmentValues.placeholders());

        assertThat(fileContent(labPlan, "inventory/group_vars/artemislocal/main.yml"))
                .isEqualTo("---\nvar_testserver_name: \"artemis-local\"\nvar_server_hostname: \"artemis.192.168.252.2.nip.io\"");
        assertThat(fileContent(labPlan, "inventory/group_vars/artemislocal/secrets.yml"))
                .contains("artemis_database_password: \"{{ lookup('hashi_vault', 'kv/data/artemis/test/artemis-local').get('db_password') }}\"");
        assertThat(fileContent(placeholderPlan, "inventory/group_vars/artemistarget/main.yml")).contains("var_testserver_name: \"REPLACE_ME_TARGET_NAME\"");
        assertThat(placeholderPlan.environmentStates()).allMatch(state -> RemoteAnsibleEmissionPlan.ENVIRONMENT_PENDING.equals(state.status()));
        assertThat(labPlan.environmentStates()).allMatch(state -> RemoteAnsibleEmissionPlan.ENVIRONMENT_PROVIDED.equals(state.status()));
    }

    @Test
    void vaultReferencesAreCollectedWithResolvedPaths() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        assertThat(plan.vaultReferences()).anySatisfy(reference -> {
            assertThat(reference.path()).isEqualTo("kv/data/artemis/test/artemis-local");
            assertThat(reference.field()).isEqualTo("build_agent_git_password");
            assertThat(reference.consumer()).isEqualTo("version_control.localvc.build_agent_git_credentials.password");
        });
        assertThat(plan.vaultReferences()).noneMatch(reference -> reference.path().contains("{vaultServerName}"));
    }

    @Test
    void noPlannedFileContainsASecretValueOrPlaceholderLeak() {
        RemoteAnsibleEmissionPlan plan = planner.plan(model, fullSelection(), labEnvironment());

        for (RemoteAnsibleEmissionPlan.PlannedFile file : plan.valuesFiles()) {
            assertThat(file.content()).as("file %s", file.path()).doesNotContain("{value}").doesNotContain("{vaultServerName}");
        }
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
        return RemoteEnvironmentValues.resolve("artemis-local", "artemis.192.168.252.2.nip.io", "Artemis Feature Model Thesis Lab", "Junting Ning",
                "artemis-local@thesis.invalid", "/opt/lab-certs/fullchain.pem", "/opt/lab-certs/privkey.pem", null);
    }

    private AnsibleBindingCatalog.FeatureBinding valueGatedBinding(List<AnsibleBindingCatalog.GatedField> gatedFields) {
        return new AnsibleBindingCatalog.FeatureBinding(AnsibleBindingCatalog.BINDING_BOUND, AnsibleBindingCatalog.GATING_VALUE_GATED, "artemistests_hyperion",
                "artemistests_hyperion.yml", null, List.of("---", "artemis_hyperion_enabled: true"), gatedFields, List.of("    temperature: 1.0"), null, null,
                null, null, null);
    }

    private AnsibleBindingCatalog.GatedField field(String name, String line) {
        return new AnsibleBindingCatalog.GatedField(name, line);
    }

    private String fileContent(RemoteAnsibleEmissionPlan plan, String path) {
        return plan.valuesFiles().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow(() ->
                new AssertionError("Missing planned file " + path)).content();
    }

    private List<String> filePaths(RemoteAnsibleEmissionPlan plan) {
        return plan.valuesFiles().stream().map(RemoteAnsibleEmissionPlan.PlannedFile::path).toList();
    }
}
