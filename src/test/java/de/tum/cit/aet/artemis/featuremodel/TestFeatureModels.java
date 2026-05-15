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

    private TestFeatureModels() {
    }

    public static FeatureModel baseModel() {
        return new FeatureModel(metadata(), List.of(root(), group("exercise-system", "Exercise System"), module("exercise-common", "Exercise Common", "enabled"),
                module("programming", "Programming", "enabled"), module("quiz", "Quiz", "enabled"), module("athena", "Athena", "disabled")),
                List.of(relation("artemis", "exercise-system", "group", 1), relation("exercise-system", "exercise-common", "mandatory", 1),
                        relation("exercise-system", "programming", "mandatory", 2), relation("exercise-system", "quiz", "mandatory", 3),
                        relation("exercise-system", "athena", "optional", 4)),
                List.of());
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
        return new FeatureModel(model.model(), model.features().stream().filter(feature -> !feature.id().equals(featureId)).toList(), model.relations(), model.constraints());
    }

    public static FeatureModel duplicateFeatureIdModel() {
        List<FeatureNode> features = new ArrayList<>(baseModel().features());
        features.add(module("programming", "Duplicate Programming", "enabled"));
        return withFeatures(features);
    }

    public static FeatureConstraint requires(String source, String target) {
        return new FeatureConstraint("requires-test", "requires", source, target, null, "Synthetic requires constraint.");
    }

    public static FeatureConstraint excludes(String source, String target) {
        return new FeatureConstraint("excludes-test", "excludes", source, target, null, "Synthetic excludes constraint.");
    }

    public static FeatureConstraint expression(JsonNode expression) {
        return new FeatureConstraint("expression-test", "expression", "programming", "athena", expression, "Synthetic expression constraint.");
    }

    private static ModelMetadata metadata() {
        return new ModelMetadata("test-model", "Test Feature Model", "0.0.1");
    }

    private static FeatureNode root() {
        return new FeatureNode("artemis", "Artemis", "root", false, null, "not_applicable", null);
    }

    private static FeatureNode group(String id, String name) {
        return new FeatureNode(id, name, "group", false, null, "not_applicable", null);
    }

    private static FeatureNode module(String id, String name, String defaultState) {
        return new FeatureNode(id, name, "module", true, null, defaultState, null);
    }

    private static FeatureRelation relation(String parentId, String childId, String relationType, int order) {
        String groupType = "group".equals(relationType) ? "and" : null;
        return new FeatureRelation(parentId, childId, relationType, groupType, order);
    }
}
