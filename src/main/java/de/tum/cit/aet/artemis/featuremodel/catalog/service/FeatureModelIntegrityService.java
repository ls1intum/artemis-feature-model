package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;

@Service
public class FeatureModelIntegrityService {

    /**
     * Validates structural integrity rules for a feature model.
     *
     * @param model model to validate.
     * @throws FeatureModelIntegrityException if a structural integrity rule is violated.
     */
    public void validate(FeatureModel model) {
        validateUniqueFeatureIds(model);
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        validateRootCount(model);
        validateRelationEndpoints(model, featuresById);
    }

    /**
     * Checks that each feature id is unique.
     *
     * @param model model to validate.
     * @throws FeatureModelIntegrityException if a duplicate feature id exists.
     */
    private void validateUniqueFeatureIds(FeatureModel model) {
        Set<String> seenIds = new HashSet<>();
        for (FeatureNode feature : model.features()) {
            if (!seenIds.add(feature.id())) {
                throw new FeatureModelIntegrityException(ValidationCode.DUPLICATE_FEATURE_ID.name(), duplicateFeatureIdMessage(feature));
            }
        }
    }

    /**
     * Builds the duplicate feature id exception message.
     *
     * @param feature duplicate feature.
     * @return exception message.
     */
    private String duplicateFeatureIdMessage(FeatureNode feature) {
        return "Feature id '" + feature.id() + "' is used more than once.";
    }

    /**
     * Checks that the model contains exactly one root feature.
     *
     * @param model model to validate.
     * @throws FeatureModelIntegrityException if the model has no root or multiple roots.
     */
    private void validateRootCount(FeatureModel model) {
        long rootCount = countRootFeatures(model);
        if (rootCount == 0) {
            throw new FeatureModelIntegrityException(ValidationCode.NO_ROOT_FEATURE.name(), "The feature model does not contain a root feature.");
        }
        if (rootCount > 1) {
            throw new FeatureModelIntegrityException(ValidationCode.MULTIPLE_ROOT_FEATURES.name(),
                    "The feature model contains more than one root feature.");
        }
    }

    /**
     * Counts root feature nodes in a model.
     *
     * @param model model to inspect.
     * @return number of root feature nodes.
     */
    private long countRootFeatures(FeatureModel model) {
        return model.features().stream().filter(FeatureNode::isRoot).count();
    }

    /**
     * Checks that every relation references existing parent and child feature ids.
     *
     * @param model model containing relations to validate.
     * @param featuresById known features keyed by id.
     * @throws FeatureModelIntegrityException if a relation references a missing feature.
     */
    private void validateRelationEndpoints(FeatureModel model, Map<String, FeatureNode> featuresById) {
        for (FeatureRelation relation : model.relations()) {
            if (!featuresById.containsKey(relation.parentId())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_RELATION_PARENT.name(),
                        "Relation parent '" + relation.parentId() + "' does not exist.");
            }
            if (!featuresById.containsKey(relation.childId())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_RELATION_CHILD.name(),
                        "Relation child '" + relation.childId() + "' does not exist.");
            }
        }
    }

}
