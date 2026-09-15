package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Covers the model-side validation rules: structural integrity and role visibility. */
class GeneratedModelValidatorTest {

    private final GeneratedModelValidator validator = new GeneratedModelValidator();

    @Test
    void passesForConsistentModel() {
        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("maintainer"), List.of("maintainer"))));

        assertThat(result.items()).isEmpty();
        assertThat(result.modelIntegrityValid()).isTrue();
        assertThat(result.deliveryEligible()).isTrue();
    }

    @Test
    void marksInvalidGeneratedModelAsSnapshotIneligible() {
        FeatureModel valid = model(technicalFeature(List.of("maintainer"), List.of("maintainer")));
        FeatureModel withoutRoot = new FeatureModel(valid.model(), valid.features().stream().filter(feature -> !feature.isRoot()).toList(), valid.relations(),
                valid.constraints());

        GeneratedModelValidator.Result result = validator.validate(withoutRoot);

        assertThat(result.modelIntegrityValid()).isFalse();
        assertThat(result.items()).anySatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_GENERATED_MODEL_INVALID));
    }

    @Test
    void danglingConstraintMakesSnapshotIneligible() {
        FeatureModel valid = model(technicalFeature(List.of("maintainer"), List.of("maintainer")));
        FeatureConstraint dangling = new FeatureConstraint("alpha-requires-ghost", "requires", "alpha", "ghost", null, "Synthetic invalid constraint.");
        FeatureModel withDanglingConstraint = new FeatureModel(valid.model(), valid.features(), valid.relations(), List.of(dangling));

        GeneratedModelValidator.Result result = validator.validate(withDanglingConstraint);

        assertThat(result.modelIntegrityValid()).isFalse();
        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_GENERATED_MODEL_INVALID);
            assertThat(item.subject()).isEqualTo("MISSING_CONSTRAINT_TARGET");
            assertThat(item.message()).contains("alpha-requires-ghost").contains("ghost");
        });
        assertThat(result.deliveryEligible()).isFalse();
    }

    @Test
    void reportsTechnicalFeatureVisibleToTeachers() {
        GeneratedModelValidator.Result result = validator.validate(model(technicalFeature(List.of("teacher", "maintainer"), List.of("maintainer"))));

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("tech-a");
        });
    }

    private FeatureModel model(FeatureNode technicalFeature) {
        FeatureNode root = new FeatureNode("root", "Root", "root", false, null, "not_applicable", null);
        FeatureNode group = new FeatureNode("alpha-group", "Alpha Group", "group", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha", "Alpha", "module", true, null, "enabled", null, "functional", List.of("teacher", "maintainer"),
                List.of("teacher", "maintainer"), List.of("alpha-service"), null, null);
        List<FeatureRelation> relations = List.of(new FeatureRelation("root", "alpha-group", "group", "and", 1),
                new FeatureRelation("alpha-group", "alpha", "optional", null, 1), new FeatureRelation("root", "tech-a", "optional", null, 2));
        return new FeatureModel(new ModelMetadata("generated-test-model", "Generated Test Model", "0.0.1"), List.of(root, group, alpha, technicalFeature),
                relations, List.of());
    }

    private FeatureNode technicalFeature(List<String> visibleTo, List<String> configurableBy) {
        return new FeatureNode("tech-a", "Tech A", "feature", true, null, "enabled", null, "technical", visibleTo, configurableBy, List.of(), null, null);
    }

}
