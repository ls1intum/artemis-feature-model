package de.tum.cit.aet.artemis.featuremodel.validation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRequest;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class FeatureModelValidationServiceTest {

    private static final List<String> VALID_BASE_SELECTION = List.of("exercise-common", "programming", "quiz");

    @Test
    void currentDefaultSelectionIsValid() {
        FeatureModelTreeService treeService = treeService();
        FeatureModelCatalogService catalogService = runtimeCatalogService(treeService);
        FeatureModel model = catalogService.loadActiveModel();
        ValidationRequest request = new ValidationRequest(catalogService.defaultSelectedFeatureIds(model));

        var result = new FeatureModelValidationService(catalogService, treeService).validateSelection(request);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void reportsMissingMandatoryFeature() {
        var result = validationService(TestFeatureModels.baseModel()).validateSelection(new ValidationRequest(List.of("exercise-common", "quiz")));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting("code").contains(ValidationCode.MANDATORY_FEATURE_MISSING.name());
        assertThat(result.violations()).anySatisfy(violation -> {
            assertThat(violation.featureIds()).containsExactly("programming");
            assertThat(violation.relation().parentId()).isEqualTo("exercise-system");
        });
    }

    @Test
    void reportsUnknownSelectedFeatureAndRemovesItFromNormalizedSelection() {
        var result = validationService(TestFeatureModels.baseModel())
                .validateSelection(new ValidationRequest(List.of("exercise-common", "programming", "quiz", "unknown-feature", "programming")));

        assertThat(result.valid()).isFalse();
        assertThat(result.normalizedSelection()).containsExactly("exercise-common", "programming", "quiz");
        assertThat(result.violations()).extracting("code").contains(ValidationCode.UNKNOWN_SELECTED_FEATURE.name());
    }

    @Test
    void reportsRequiresConstraintViolation() {
        FeatureModel model = TestFeatureModels.withConstraints(TestFeatureModels.requires("programming", "athena"));

        var result = validationService(model).validateSelection(new ValidationRequest(VALID_BASE_SELECTION));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting("code").contains(ValidationCode.REQUIRES_CONSTRAINT_VIOLATED.name());
    }

    @Test
    void reportsExcludesConstraintViolation() {
        FeatureModel model = TestFeatureModels.withConstraints(TestFeatureModels.excludes("programming", "athena"));

        var result = validationService(model).validateSelection(new ValidationRequest(List.of("exercise-common", "programming", "quiz", "athena")));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting("code").contains(ValidationCode.EXCLUDES_CONSTRAINT_VIOLATED.name());
    }

    @Test
    void reportsActiveAlternativeGroupWithoutASelectedChild() {
        var result = validationService(alternativeGroupModel()).validateSelection(new ValidationRequest(List.of("deployment")));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo(ValidationCode.ALTERNATIVE_GROUP_SELECTION_MISSING.name());
            assertThat(violation.featureIds()).containsExactly("mysql", "postgresql");
            assertThat(violation.relation().parentId()).isEqualTo("deployment");
            assertThat(violation.relation().childId()).isEqualTo("database");
            assertThat(violation.message()).isEqualTo("Database requires at least one selected alternative.");
            assertThat(violation.suggestion()).isEqualTo("Enable one of: MySQL, PostgreSQL.");
        });
    }

    @Test
    void acceptsOneSelectedAlternative() {
        var result = validationService(alternativeGroupModel()).validateSelection(new ValidationRequest(List.of("deployment", "mysql")));

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void existingExcludesConstraintRejectsBothSelectedAlternatives() {
        var result = validationService(alternativeGroupModel())
                .validateSelection(new ValidationRequest(List.of("deployment", "mysql", "postgresql")));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting("code").containsExactly(ValidationCode.EXCLUDES_CONSTRAINT_VIOLATED.name());
    }

    @Test
    void ignoresAlternativeGroupBelowDeselectedParent() {
        var result = validationService(alternativeGroupModel()).validateSelection(new ValidationRequest(List.of()));

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void reportsUnsupportedExpressionConstraintAsWarningOnly() {
        FeatureModel model = TestFeatureModels.withConstraints(TestFeatureModels.expression(null));

        var result = validationService(model).validateSelection(new ValidationRequest(VALID_BASE_SELECTION));

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.warnings()).extracting("code").containsExactly(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name());
    }

    private FeatureModelValidationService validationService(FeatureModel model) {
        FeatureModelTreeService treeService = treeService();
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(() -> model, new FeatureModelIntegrityService(), treeService);
        return new FeatureModelValidationService(catalogService, treeService);
    }

    private FeatureModelCatalogService runtimeCatalogService(FeatureModelTreeService treeService) {
        JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());
        return new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
    }

    private FeatureModelTreeService treeService() {
        return new FeatureModelTreeService();
    }

    private FeatureModel alternativeGroupModel() {
        List<FeatureNode> features = List.of(feature("artemis", "Artemis", "root", false), feature("deployment", "Deployment", "feature", true),
                feature("database", "Database", "group", false), feature("mysql", "MySQL", "feature", true),
                feature("postgresql", "PostgreSQL", "feature", true));
        List<FeatureRelation> relations = List.of(new FeatureRelation("artemis", "deployment", "optional", null, 1),
                new FeatureRelation("deployment", "database", "group", "alternative", 1),
                new FeatureRelation("database", "mysql", "optional", null, 1), new FeatureRelation("database", "postgresql", "optional", null, 2));
        List<FeatureConstraint> constraints = List.of(new FeatureConstraint("database-excludes", "excludes", "mysql", "postgresql", null,
                "Exactly one database is selected."));
        return new FeatureModel(new ModelMetadata("alternative-test", "Alternative Test", "0.0.1"), features, relations, constraints);
    }

    private FeatureNode feature(String id, String name, String kind, boolean selectable) {
        String defaultState = selectable ? "disabled" : "not_applicable";
        return new FeatureNode(id, name, kind, selectable, null, defaultState, null);
    }
}
