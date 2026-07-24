package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import tools.jackson.databind.ObjectMapper;

class TechnicalSelectionResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TechnicalSelectionResolver resolver = new TechnicalSelectionResolver();

    @Test
    void resolvesTokensAndDatabaseInFeatureModelOrder() {
        FeatureModel model = technicalModel();

        TechnicalSelection selection = resolver.resolve(model, Set.of("integrated-code-lifecycle", "localvc", "mysql"));

        assertThat(selection.springProfileTokens()).containsExactly("localci", "buildagent", "localvc");
        assertThat(selection.databaseComposeFile()).contains("docker/mysql.yml");
        assertThat(selection.databaseId()).contains("mysql");
        assertThat(selection.ciProviderId()).contains("integrated-code-lifecycle");
    }

    @Test
    void deselectedFeaturesContributeNothing() {
        TechnicalSelection selection = resolver.resolve(technicalModel(), Set.of());

        assertThat(selection.isEmpty()).isTrue();
        assertThat(selection.springProfileTokens()).isEmpty();
        assertThat(selection.databaseComposeFile()).isEmpty();
    }

    @Test
    void rejectsUnknownStructuralTargetsWithAControlledError() {
        FeatureNode root = feature("artemis", "root", false, List.of());
        FeatureNode unknown = feature("unknown", "feature", true, List.of(mapping("future.yml", "future.path", "value")));
        FeatureModel model = model(List.of(root, unknown), List.of());

        assertThatThrownBy(() -> resolver.resolve(model, Set.of("unknown"))).isInstanceOfSatisfying(ArtifactGenerationException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_UNSUPPORTED_TECHNICAL_MAPPING");
            assertThat(exception.getStatus().value()).isEqualTo(400);
            assertThat(exception).hasMessageContaining("future.yml:future.path");
        });
    }

    @Test
    void curatedBundledModelResolvesToEmpty() {
        FeatureModel model = new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper).loadActiveModel();
        Set<String> selectedFeatureIds = new LinkedHashSet<>();
        for (FeatureNode feature : model.features()) {
            selectedFeatureIds.add(feature.id());
        }

        assertThat(resolver.resolve(model, selectedFeatureIds).isEmpty()).isTrue();
    }

    private FeatureModel technicalModel() {
        FeatureNode root = feature("artemis", "root", false, List.of());
        FeatureNode database = feature("database", "group", false, List.of());
        FeatureNode ciProvider = feature("ci-provider", "group", false, List.of());
        FeatureNode integrated = feature("integrated-code-lifecycle", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localci,buildagent")));
        FeatureNode localvc = feature("localvc", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localvc")));
        FeatureNode mysql = feature("mysql", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.COMPOSE_TARGET, TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/mysql.yml")));
        List<FeatureNode> features = List.of(root, database, ciProvider, integrated, localvc, mysql);
        List<FeatureRelation> relations = List.of(new FeatureRelation("artemis", "database", "group", "alternative", 1),
                new FeatureRelation("database", "mysql", "optional", null, 1),
                new FeatureRelation("artemis", "ci-provider", "group", "alternative", 2),
                new FeatureRelation("ci-provider", "integrated-code-lifecycle", "optional", null, 1),
                new FeatureRelation("artemis", "localvc", "mandatory", null, 3));
        return model(features, relations);
    }

    private FeatureModel model(List<FeatureNode> features, List<FeatureRelation> relations) {
        return new FeatureModel(new ModelMetadata("technical-test", "Technical Test", "0.0.1"), features, relations, List.of());
    }

    private FeatureNode feature(String id, String kind, boolean selectable, List<ArtifactMapping> mappings) {
        String defaultState = selectable ? "disabled" : "not_applicable";
        String category = selectable ? "technical" : "derived";
        return new FeatureNode(id, id, kind, selectable, null, defaultState, null, category, List.of(), List.of(), List.of(), mappings, null);
    }

    private ArtifactMapping mapping(String target, String path, String value) {
        return new ArtifactMapping(target, path, objectMapper.valueToTree(value), null, null, false, false);
    }
}
