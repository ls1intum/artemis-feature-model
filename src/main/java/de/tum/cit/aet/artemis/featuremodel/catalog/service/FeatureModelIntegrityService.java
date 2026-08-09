package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
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
        validateConstraintEndpoints(model, featuresById);
        validateArtifactMappings(model);
        validateEnvironmentNameCollisions(model);
    }

    /**
     * Checks that every artifact mapping declares exactly one valid form: a known explicit source, a non-blank target
     * and path, at least one toggle value for a selection mapping, and no toggle value for an environment mapping.
     *
     * @param model model to validate.
     * @throws FeatureModelIntegrityException if a mapping is malformed, mixes forms, or declares an unknown source.
     */
    private void validateArtifactMappings(FeatureModel model) {
        for (FeatureNode feature : model.features()) {
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                validateArtifactMapping(feature, mapping);
            }
        }
    }

    /**
     * Validates one artifact mapping of a feature.
     *
     * @param feature feature owning the mapping.
     * @param mapping mapping to validate.
     * @throws FeatureModelIntegrityException if the mapping violates the explicit-source contract.
     */
    private void validateArtifactMapping(FeatureNode feature, ArtifactMapping mapping) {
        if (mapping.target() == null || mapping.target().isBlank() || mapping.path() == null || mapping.path().isBlank()) {
            throw invalidMapping(feature, mapping, "must declare a non-blank target and path");
        }
        if (!ArtifactMappingSource.isKnown(mapping.source())) {
            throw invalidMapping(feature, mapping, "must declare source '" + ArtifactMappingSource.SELECTION + "' or '"
                    + ArtifactMappingSource.ENVIRONMENT + "', but declares '" + mapping.source() + "'");
        }
        boolean hasToggleValue = mapping.valueWhenSelected() != null || mapping.valueWhenDeselected() != null;
        if (mapping.isSelection() && !hasToggleValue) {
            throw invalidMapping(feature, mapping, "declares source 'selection' but no selected or deselected value");
        }
        if (mapping.isEnvironment() && hasToggleValue) {
            throw invalidMapping(feature, mapping, "declares source 'environment' but carries a selection value");
        }
    }

    /**
     * Builds the invalid artifact mapping exception.
     *
     * @param feature feature owning the mapping.
     * @param mapping invalid mapping.
     * @param problem description of the violated rule.
     * @return integrity exception describing the mapping.
     */
    private FeatureModelIntegrityException invalidMapping(FeatureNode feature, ArtifactMapping mapping, String problem) {
        return new FeatureModelIntegrityException(ValidationCode.INVALID_ARTIFACT_MAPPING.name(),
                "Artifact mapping '" + mapping.path() + "' of feature '" + feature.id() + "' " + problem + ".");
    }

    /**
     * Checks that no two environment-sourced configuration paths derive the same environment variable name anywhere
     * in the model. The derivation collapses runs of non-alphanumeric characters, so distinct paths such as
     * {@code artemis.foo-bar.x} and {@code artemis.foo.bar.x} would otherwise silently share one variable.
     *
     * @param model model to validate.
     * @throws FeatureModelIntegrityException if two different paths derive the same environment variable name.
     */
    private void validateEnvironmentNameCollisions(FeatureModel model) {
        Map<String, String> pathsByDerivedName = new HashMap<>();
        for (FeatureNode feature : model.features()) {
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (!mapping.isEnvironment()) {
                    continue;
                }
                String derivedName;
                try {
                    derivedName = EnvironmentVariableNames.derive(mapping.path());
                }
                catch (IllegalArgumentException e) {
                    throw new FeatureModelIntegrityException(ValidationCode.INVALID_ARTIFACT_MAPPING.name(),
                            "Artifact mapping '" + mapping.path() + "' of feature '" + feature.id() + "' derives an empty environment variable name.");
                }
                String existingPath = pathsByDerivedName.putIfAbsent(derivedName, mapping.path());
                if (existingPath != null && !existingPath.equals(mapping.path())) {
                    throw new FeatureModelIntegrityException(ValidationCode.ENVIRONMENT_NAME_COLLISION.name(),
                            "Configuration paths '" + existingPath + "' and '" + mapping.path() + "' derive the same environment variable name '"
                                    + derivedName + "'.");
                }
            }
        }
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

    /**
     * Checks that requires and excludes constraints reference existing source and target feature ids. Expression
     * constraints retain their expression-owned endpoint contract.
     *
     * @param model model containing constraints to validate.
     * @param featuresById known features keyed by id.
     * @throws FeatureModelIntegrityException if a non-expression constraint references a missing feature.
     */
    private void validateConstraintEndpoints(FeatureModel model, Map<String, FeatureNode> featuresById) {
        for (FeatureConstraint constraint : model.constraints()) {
            if (!constraint.isRequires() && !constraint.isExcludes()) {
                continue;
            }
            if (constraint.source() == null || !featuresById.containsKey(constraint.source())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_CONSTRAINT_SOURCE.name(),
                        "Constraint '" + constraint.id() + "' source '" + constraint.source() + "' does not exist.");
            }
            if (constraint.target() == null || !featuresById.containsKey(constraint.target())) {
                throw new FeatureModelIntegrityException(ValidationCode.MISSING_CONSTRAINT_TARGET.name(),
                        "Constraint '" + constraint.id() + "' target '" + constraint.target() + "' does not exist.");
            }
        }
    }

}
