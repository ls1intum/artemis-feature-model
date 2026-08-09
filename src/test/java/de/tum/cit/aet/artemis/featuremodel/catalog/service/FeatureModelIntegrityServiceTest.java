package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;
import tools.jackson.databind.node.JsonNodeFactory;

class FeatureModelIntegrityServiceTest {

    private final FeatureModelIntegrityService service = new FeatureModelIntegrityService();

    @Test
    void acceptsValidModel() {
        assertThatCode(() -> service.validate(TestFeatureModels.baseModel())).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateFeatureIds() {
        assertThatThrownBy(() -> service.validate(TestFeatureModels.duplicateFeatureIdModel())).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.DUPLICATE_FEATURE_ID.name());
    }

    @Test
    void rejectsMissingRelationParent() {
        FeatureRelation invalidRelation = new FeatureRelation("missing-parent", "programming", "mandatory", null, 1);

        assertThatThrownBy(() -> validateModelWithRelation(invalidRelation)).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.MISSING_RELATION_PARENT.name());
    }

    @Test
    void rejectsMissingRelationChild() {
        FeatureRelation invalidRelation = new FeatureRelation("exercise-system", "missing-child", "mandatory", null, 1);

        assertThatThrownBy(() -> validateModelWithRelation(invalidRelation)).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.MISSING_RELATION_CHILD.name());
    }

    @Test
    void acceptsValidRequiresConstraint() {
        assertThatCode(() -> service.validate(TestFeatureModels.withConstraints(TestFeatureModels.requires("programming", "athena"))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsValidExcludesConstraint() {
        assertThatCode(() -> service.validate(TestFeatureModels.withConstraints(TestFeatureModels.excludes("programming", "quiz"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingConstraintSource() {
        FeatureConstraint invalidConstraint = new FeatureConstraint("missing-source", "requires", null, "programming", null, "Synthetic invalid constraint.");

        assertThatThrownBy(() -> service.validate(TestFeatureModels.withConstraints(invalidConstraint))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.MISSING_CONSTRAINT_SOURCE.name()).hasMessageContaining("missing-source");
    }

    @Test
    void rejectsMissingConstraintTarget() {
        FeatureConstraint invalidConstraint = TestFeatureModels.requires("programming", "missing-target");

        assertThatThrownBy(() -> service.validate(TestFeatureModels.withConstraints(invalidConstraint))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.MISSING_CONSTRAINT_TARGET.name()).hasMessageContaining("missing-target");
    }

    @Test
    void leavesExpressionConstraintEndpointsToExpressionValidation() {
        FeatureConstraint expression = new FeatureConstraint("expression", "expression", null, null, null, "Synthetic expression constraint.");

        assertThatCode(() -> service.validate(TestFeatureModels.withConstraints(expression))).doesNotThrowAnyException();
    }

    @Test
    void rejectsModelWithoutRoot() {
        assertThatThrownBy(() -> service.validate(TestFeatureModels.withoutFeature("artemis"))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.NO_ROOT_FEATURE.name());
    }

    @Test
    void rejectsMultipleRoots() {
        List<FeatureNode> features = new ArrayList<>(TestFeatureModels.baseModel().features());
        features.add(new FeatureNode("second-root", "Second Root", "root", false, null, "not_applicable", null));

        assertThatThrownBy(() -> service.validate(TestFeatureModels.withFeatures(features))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.MULTIPLE_ROOT_FEATURES.name());
    }

    @Test
    void acceptsBothExplicitMappingForms() {
        List<ArtifactMapping> mappings = List.of(selectionMapping("artemis.demo.enabled"), environmentMapping("artemis.demo.url"));

        assertThatCode(() -> service.validate(modelWithMappings(mappings))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownMappingSource() {
        ArtifactMapping mapping = new ArtifactMapping("application-feature-model.yml", "artemis.demo.enabled", "profile",
                JsonNodeFactory.instance.booleanNode(true), null, null);

        assertThatThrownBy(() -> service.validate(modelWithMappings(List.of(mapping)))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.INVALID_ARTIFACT_MAPPING.name()).hasMessageContaining("profile");
    }

    @Test
    void rejectsMappingWithoutSource() {
        ArtifactMapping mapping = new ArtifactMapping("application-feature-model.yml", "artemis.demo.enabled", null,
                JsonNodeFactory.instance.booleanNode(true), null, null);

        assertThatThrownBy(() -> service.validate(modelWithMappings(List.of(mapping)))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.INVALID_ARTIFACT_MAPPING.name());
    }

    @Test
    void rejectsSelectionMappingWithoutAnyValue() {
        ArtifactMapping mapping = new ArtifactMapping("application-feature-model.yml", "artemis.demo.enabled", ArtifactMappingSource.SELECTION, null, null,
                null);

        assertThatThrownBy(() -> service.validate(modelWithMappings(List.of(mapping)))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.INVALID_ARTIFACT_MAPPING.name()).hasMessageContaining("selection");
    }

    @Test
    void rejectsEnvironmentMappingCarryingASelectionValue() {
        ArtifactMapping mapping = new ArtifactMapping("application-feature-model.yml", "artemis.demo.url", ArtifactMappingSource.ENVIRONMENT,
                JsonNodeFactory.instance.stringNode("https://demo.invalid"), null, null);

        assertThatThrownBy(() -> service.validate(modelWithMappings(List.of(mapping)))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.INVALID_ARTIFACT_MAPPING.name()).hasMessageContaining("environment");
    }

    @Test
    void rejectsMappingWithBlankTargetOrPath() {
        ArtifactMapping mapping = new ArtifactMapping(" ", "artemis.demo.enabled", ArtifactMappingSource.SELECTION,
                JsonNodeFactory.instance.booleanNode(true), null, null);

        assertThatThrownBy(() -> service.validate(modelWithMappings(List.of(mapping)))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.INVALID_ARTIFACT_MAPPING.name());
    }

    @Test
    void rejectsEnvironmentNameCollisionsAcrossTheModel() {
        List<ArtifactMapping> mappings = List.of(environmentMapping("artemis.foo-bar.x"), environmentMapping("artemis.foo.bar.x"));

        assertThatThrownBy(() -> service.validate(modelWithMappings(mappings))).isInstanceOf(FeatureModelIntegrityException.class)
                .hasFieldOrPropertyWithValue("code", ValidationCode.ENVIRONMENT_NAME_COLLISION.name()).hasMessageContaining("ARTEMIS_FOO_BAR_X");
    }

    @Test
    void acceptsTheSameEnvironmentPathOnTwoFeatures() {
        List<FeatureNode> features = new ArrayList<>(TestFeatureModels.baseModel().features());
        features.add(featureWithMappings("mapping-holder", List.of(environmentMapping("artemis.demo.url"))));
        features.add(featureWithMappings("second-holder", List.of(environmentMapping("artemis.demo.url"))));

        assertThatCode(() -> service.validate(TestFeatureModels.withFeatures(features))).doesNotThrowAnyException();
    }

    private ArtifactMapping selectionMapping(String path) {
        return new ArtifactMapping("application-feature-model.yml", path, ArtifactMappingSource.SELECTION, JsonNodeFactory.instance.booleanNode(true),
                JsonNodeFactory.instance.booleanNode(false), null);
    }

    private ArtifactMapping environmentMapping(String path) {
        return new ArtifactMapping("application-feature-model.yml", path, ArtifactMappingSource.ENVIRONMENT, null, null, null);
    }

    private de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel modelWithMappings(List<ArtifactMapping> mappings) {
        List<FeatureNode> features = new ArrayList<>(TestFeatureModels.baseModel().features());
        features.add(featureWithMappings("mapping-holder", mappings));
        return TestFeatureModels.withFeatures(features);
    }

    private FeatureNode featureWithMappings(String id, List<ArtifactMapping> mappings) {
        return new FeatureNode(id, id, "feature", true, null, "disabled", null, null, List.of(), List.of(), List.of(), mappings, null);
    }

    private void validateModelWithRelation(FeatureRelation relation) {
        service.validate(TestFeatureModels.withRelations(List.of(relation)));
    }
}
