package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

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
    void curatedBundledModelResolvesItsDefaultTechnicalStack() {
        FeatureModel model = new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper).loadActiveModel();
        Set<String> selectedFeatureIds = Set.of("mysql", "integrated-code-lifecycle", "localvc");

        TechnicalSelection selection = resolver.resolve(model, selectedFeatureIds);

        assertThat(selection.databaseId()).contains("mysql");
        assertThat(selection.databaseComposeFile()).contains("docker/mysql.yml");
        assertThat(selection.ciProviderId()).contains("integrated-code-lifecycle");
        assertThat(selection.springProfileTokens()).containsExactly("localci", "buildagent", "localvc");
    }

    @Test
    void identifiesTheCiOwnerAfterItsAlternativeGroupIsRenamed() {
        FeatureModel original = technicalModel();
        List<FeatureRelation> renamedRelations = renamedRelations(original, "ci-provider", "build-system");
        List<FeatureNode> renamedFeatures = renamedFeatures(original, "ci-provider", "build-system");
        FeatureModel renamed = model(renamedFeatures, renamedRelations);

        TechnicalSelection selection = resolver.resolve(renamed, Set.of("integrated-code-lifecycle", "localvc"));

        assertThat(selection.ciProviderId()).contains("integrated-code-lifecycle");
    }

    @Test
    void rejectsAbsentSelectedValues() {
        assertInvalidMappingValue(null);
    }

    @Test
    void rejectsNonTextSelectedValues() {
        assertInvalidMappingValue(objectMapper.valueToTree(42));
    }

    @Test
    void rejectsBlankSelectedValues() {
        assertInvalidMappingValue(objectMapper.valueToTree("  "));
    }

    @Test
    void rejectsTwoSelectedCiOwners() {
        assertThatThrownBy(() -> resolver.resolve(technicalModel(), Set.of("integrated-code-lifecycle", "jenkins")))
                .isInstanceOfSatisfying(ArtifactGenerationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_CONFLICTING_TECHNICAL_SELECTION"));
    }

    @Test
    void rejectsTwoDifferentDatabaseComposeFiles() {
        assertThatThrownBy(() -> resolver.resolve(technicalModel(), Set.of("mysql", "postgresql")))
                .isInstanceOfSatisfying(ArtifactGenerationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_CONFLICTING_TECHNICAL_SELECTION"));
    }

    private FeatureModel technicalModel() {
        FeatureNode root = feature("artemis", "root", false, List.of());
        FeatureNode database = feature("database", "group", false, List.of());
        FeatureNode ciProvider = feature("ci-provider", "group", false, List.of());
        FeatureNode integrated = feature("integrated-code-lifecycle", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localci,buildagent")));
        FeatureNode localvc = feature("localvc", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localvc")));
        FeatureNode jenkins = feature("jenkins", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH, "jenkins")));
        FeatureNode mysql = feature("mysql", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.COMPOSE_TARGET, TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/mysql.yml")));
        FeatureNode postgresql = feature("postgresql", "feature", true,
                List.of(mapping(TechnicalSelectionResolver.COMPOSE_TARGET, TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/postgres.yml")));
        List<FeatureNode> features = List.of(root, database, ciProvider, integrated, jenkins, localvc, mysql, postgresql);
        List<FeatureRelation> relations = List.of(new FeatureRelation("artemis", "database", "group", "alternative", 1),
                new FeatureRelation("database", "mysql", "optional", null, 1),
                new FeatureRelation("database", "postgresql", "optional", null, 2),
                new FeatureRelation("artemis", "ci-provider", "group", "alternative", 2),
                new FeatureRelation("ci-provider", "integrated-code-lifecycle", "optional", null, 1),
                new FeatureRelation("ci-provider", "jenkins", "optional", null, 2),
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
        return new ArtifactMapping(target, path, ArtifactMappingSource.SELECTION, objectMapper.valueToTree(value), null, false);
    }

    private void assertInvalidMappingValue(JsonNode value) {
        FeatureNode root = feature("artemis", "root", false, List.of());
        ArtifactMapping invalidMapping = new ArtifactMapping(TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, ArtifactMappingSource.SELECTION, value, null, false);
        FeatureNode invalid = feature("invalid", "feature", true, List.of(invalidMapping));
        FeatureModel model = model(List.of(root, invalid), List.of());

        assertThatThrownBy(() -> resolver.resolve(model, Set.of("invalid")))
                .isInstanceOfSatisfying(ArtifactGenerationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_INVALID_TECHNICAL_MAPPING"));
    }

    private List<FeatureRelation> renamedRelations(FeatureModel model, String currentId, String replacementId) {
        List<FeatureRelation> renamed = new ArrayList<>();
        for (FeatureRelation relation : model.relations()) {
            renamed.add(renameGroup(relation, currentId, replacementId));
        }
        return renamed;
    }

    private List<FeatureNode> renamedFeatures(FeatureModel model, String currentId, String replacementId) {
        List<FeatureNode> renamed = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            FeatureNode renamedFeature = currentId.equals(feature.id())
                    ? feature(replacementId, "group", false, List.of())
                    : feature;
            renamed.add(renamedFeature);
        }
        return renamed;
    }

    private FeatureRelation renameGroup(FeatureRelation relation, String currentId, String replacementId) {
        String parentId = currentId.equals(relation.parentId()) ? replacementId : relation.parentId();
        String childId = currentId.equals(relation.childId()) ? replacementId : relation.childId();
        return new FeatureRelation(parentId, childId, relation.relationType(), relation.groupType(), relation.order());
    }
}
