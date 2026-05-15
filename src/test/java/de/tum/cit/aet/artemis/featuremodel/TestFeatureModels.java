package de.tum.cit.aet.artemis.featuremodel;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import tools.jackson.databind.JsonNode;

public final class TestFeatureModels {

    private static final String FEATURE_ID_ARTEMIS = "artemis";

    private static final String FEATURE_ID_EXERCISE_SYSTEM = "exercise-system";

    private static final String FEATURE_ID_EXERCISE_COMMON = "exercise-common";

    private static final String FEATURE_ID_PROGRAMMING = "programming";

    private static final String FEATURE_ID_QUIZ = "quiz";

    private static final String FEATURE_ID_ATHENA = "athena";

    private static final String DEFAULT_STATE_ENABLED = "enabled";

    private static final String DEFAULT_STATE_DISABLED = "disabled";

    private static final String DEFAULT_STATE_NOT_APPLICABLE = "not_applicable";

    private static final String KIND_MODULE = "module";

    private static final String KIND_GROUP = "group";

    private static final String KIND_ROOT = "root";

    private static final String GROUP_TYPE_AND = "and";

    private static final String RELATION_TYPE_GROUP = "group";

    private static final String RELATION_TYPE_MANDATORY = "mandatory";

    private static final String RELATION_TYPE_OPTIONAL = "optional";

    private TestFeatureModels() {
    }

    public static FeatureModel baseModel() {
        return new FeatureModel(metadata(), baseFeatures(), baseRelations(), List.of());
    }

    public static FeatureModel withConstraints(FeatureConstraint... constraints) {
        FeatureModel model = baseModel();
        return new FeatureModel(model.model(), model.features(), model.relations(), List.of(constraints));
    }

    public static FeatureModel withFeatures(List<FeatureNode> features) {
        FeatureModel model = baseModel();
        return new FeatureModel(model.model(), features, model.relations(), model.constraints());
    }

    public static FeatureModel withRelations(List<FeatureRelation> relations) {
        FeatureModel model = baseModel();
        return new FeatureModel(model.model(), model.features(), relations, model.constraints());
    }

    public static FeatureModel withoutFeature(String featureId) {
        FeatureModel model = baseModel();
        List<FeatureNode> remainingFeatures = model.features().stream().filter(feature -> !feature.id().equals(featureId)).toList();
        return new FeatureModel(model.model(), remainingFeatures, model.relations(), model.constraints());
    }

    public static FeatureModel duplicateFeatureIdModel() {
        List<FeatureNode> features = new ArrayList<>(baseModel().features());
        features.add(module(FEATURE_ID_PROGRAMMING, "Duplicate Programming", DEFAULT_STATE_ENABLED));
        return withFeatures(features);
    }

    public static FeatureConstraint requires(String source, String target) {
        return new FeatureConstraint("requires-test", "requires", source, target, null, "Synthetic requires constraint.");
    }

    public static FeatureConstraint excludes(String source, String target) {
        return new FeatureConstraint("excludes-test", "excludes", source, target, null, "Synthetic excludes constraint.");
    }

    public static FeatureConstraint expression(JsonNode expression) {
        return new FeatureConstraint("expression-test", "expression", FEATURE_ID_PROGRAMMING, FEATURE_ID_ATHENA, expression,
                "Synthetic expression constraint.");
    }

    private static List<FeatureNode> baseFeatures() {
        return List.of(root(), group(FEATURE_ID_EXERCISE_SYSTEM, "Exercise System"),
                module(FEATURE_ID_EXERCISE_COMMON, "Exercise Common", DEFAULT_STATE_ENABLED),
                module(FEATURE_ID_PROGRAMMING, "Programming", DEFAULT_STATE_ENABLED),
                module(FEATURE_ID_QUIZ, "Quiz", DEFAULT_STATE_ENABLED), module(FEATURE_ID_ATHENA, "Athena", DEFAULT_STATE_DISABLED));
    }

    private static List<FeatureRelation> baseRelations() {
        return List.of(relation(FEATURE_ID_ARTEMIS, FEATURE_ID_EXERCISE_SYSTEM, RELATION_TYPE_GROUP, 1),
                relation(FEATURE_ID_EXERCISE_SYSTEM, FEATURE_ID_EXERCISE_COMMON, RELATION_TYPE_MANDATORY, 1),
                relation(FEATURE_ID_EXERCISE_SYSTEM, FEATURE_ID_PROGRAMMING, RELATION_TYPE_MANDATORY, 2),
                relation(FEATURE_ID_EXERCISE_SYSTEM, FEATURE_ID_QUIZ, RELATION_TYPE_MANDATORY, 3),
                relation(FEATURE_ID_EXERCISE_SYSTEM, FEATURE_ID_ATHENA, RELATION_TYPE_OPTIONAL, 4));
    }

    private static ModelMetadata metadata() {
        return new ModelMetadata("test-model", "Test Feature Model", "0.0.1");
    }

    private static FeatureNode root() {
        return new FeatureNode(FEATURE_ID_ARTEMIS, "Artemis", KIND_ROOT, false, null, DEFAULT_STATE_NOT_APPLICABLE, null);
    }

    private static FeatureNode group(String id, String name) {
        return new FeatureNode(id, name, KIND_GROUP, false, null, DEFAULT_STATE_NOT_APPLICABLE, null);
    }

    private static FeatureNode module(String id, String name, String defaultState) {
        return new FeatureNode(id, name, KIND_MODULE, true, null, defaultState, null);
    }

    private static FeatureRelation relation(String parentId, String childId, String relationType, int order) {
        String groupType = RELATION_TYPE_GROUP.equals(relationType) ? GROUP_TYPE_AND : null;
        return new FeatureRelation(parentId, childId, relationType, groupType, order);
    }
}
