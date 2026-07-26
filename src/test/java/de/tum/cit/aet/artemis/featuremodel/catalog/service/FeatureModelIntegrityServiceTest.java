package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;

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

    private void validateModelWithRelation(FeatureRelation relation) {
        service.validate(TestFeatureModels.withRelations(List.of(relation)));
    }
}
