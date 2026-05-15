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

    public void validate(FeatureModel model) {
        validateUniqueFeatureIds(model);
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        validateRootCount(model);
        validateRelationEndpoints(model, featuresById);
    }

    private void validateUniqueFeatureIds(FeatureModel model) {
        Set<String> seenIds = new HashSet<>();
        model.features().stream().map(FeatureNode::id).filter(id -> !seenIds.add(id)).findFirst().ifPresent(duplicateId -> {
            throw new FeatureModelIntegrityException(ValidationCode.DUPLICATE_FEATURE_ID.name(), "Feature id '" + duplicateId + "' is used more than once.");
        });
    }

    private void validateRootCount(FeatureModel model) {
        ListRootCount rootCount = new ListRootCount(model.features().stream().filter(FeatureNode::isRoot).count());
        if (rootCount.value() == 0) {
            throw new FeatureModelIntegrityException(ValidationCode.NO_ROOT_FEATURE.name(), "The feature model does not contain a root feature.");
        }
        if (rootCount.value() > 1) {
            throw new FeatureModelIntegrityException(ValidationCode.MULTIPLE_ROOT_FEATURES.name(), "The feature model contains more than one root feature.");
        }
    }

    private void validateRelationEndpoints(FeatureModel model, Map<String, FeatureNode> featuresById) {
        for (FeatureRelation relation : model.relations()) {
            if (!featuresById.containsKey(relation.parentId())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_RELATION_PARENT.name(),
                        "Relation parent '" + relation.parentId() + "' does not exist.");
            }
            if (!featuresById.containsKey(relation.childId())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_RELATION_CHILD.name(), "Relation child '" + relation.childId() + "' does not exist.");
            }
        }
    }

    private record ListRootCount(long value) {
    }
}
