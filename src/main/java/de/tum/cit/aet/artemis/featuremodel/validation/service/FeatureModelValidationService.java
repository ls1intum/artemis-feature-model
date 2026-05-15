package de.tum.cit.aet.artemis.featuremodel.validation.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.NormalizedSelection;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRelationDTO;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationResultDTO;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationViolationDTO;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationWarningDTO;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;

@Service
public class FeatureModelValidationService {

    private final FeatureModelCatalogService catalogService;

    private final FeatureModelTreeService treeService;

    public FeatureModelValidationService(FeatureModelCatalogService catalogService, FeatureModelTreeService treeService) {
        this.catalogService = catalogService;
        this.treeService = treeService;
    }

    public ValidationResultDTO validateSelection(ValidationRequest request) {
        FeatureModel model = catalogService.loadActiveModel();
        return validateSelection(model, request);
    }

    public ValidationResultDTO validateSelection(FeatureModel model, ValidationRequest request) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        NormalizedSelection normalizedSelection = normalizeSelection(model, request, featuresById);
        Set<String> selectedKnownIds = Set.copyOf(normalizedSelection.selectedFeatureIds());

        List<ValidationViolationDTO> violations = java.util.stream.Stream
                .concat(unknownFeatureViolations(request, featuresById).stream(),
                        java.util.stream.Stream.concat(mandatoryRelationViolations(model, featuresById, selectedKnownIds).stream(),
                                constraintViolations(model, selectedKnownIds).stream()))
                .toList();
        List<ValidationWarningDTO> warnings = expressionWarnings(model);
        return new ValidationResultDTO(violations.isEmpty(), normalizedSelection.selectedFeatureIds(), violations, warnings);
    }

    private NormalizedSelection normalizeSelection(FeatureModel model, ValidationRequest request, Map<String, FeatureNode> featuresById) {
        Set<String> submittedIds = new LinkedHashSet<>(request.selectedFeatureIds());
        List<String> orderedKnownSelection = treeService.treeOrderedFeatureIds(model).stream().filter(submittedIds::contains).toList();
        List<String> orderedUnknownKnownIds = submittedIds.stream().filter(featuresById::containsKey).filter(id -> !orderedKnownSelection.contains(id)).toList();
        List<String> normalizedSelection = java.util.stream.Stream.concat(orderedKnownSelection.stream(), orderedUnknownKnownIds.stream()).toList();
        return new NormalizedSelection(normalizedSelection, submittedIds);
    }

    private List<ValidationViolationDTO> unknownFeatureViolations(ValidationRequest request, Map<String, FeatureNode> featuresById) {
        return request.selectedFeatureIds().stream().distinct().filter(id -> !featuresById.containsKey(id))
                .map(id -> new ValidationViolationDTO(ValidationCode.UNKNOWN_SELECTED_FEATURE.name(), "Selected feature '" + id + "' does not exist.", List.of(id), null,
                        "Remove '" + id + "' from the selection."))
                .toList();
    }

    private List<ValidationViolationDTO> mandatoryRelationViolations(FeatureModel model, Map<String, FeatureNode> featuresById, Set<String> selectedKnownIds) {
        Set<String> activeFeatureIds = model.features().stream().filter(feature -> feature.isRoot() || feature.isGroup() || selectedKnownIds.contains(feature.id()))
                .map(FeatureNode::id).collect(Collectors.toSet());
        return model.relations().stream().filter(FeatureRelation::isMandatory).filter(relation -> activeFeatureIds.contains(relation.parentId()))
                .filter(relation -> isSelectableModule(featuresById.get(relation.childId()))).filter(relation -> !selectedKnownIds.contains(relation.childId()))
                .map(relation -> mandatoryViolation(relation, featuresById.get(relation.childId()), featuresById.get(relation.parentId()))).toList();
    }

    private boolean isSelectableModule(FeatureNode feature) {
        return feature != null && feature.selectable();
    }

    private ValidationViolationDTO mandatoryViolation(FeatureRelation relation, FeatureNode child, FeatureNode parent) {
        return new ValidationViolationDTO(ValidationCode.MANDATORY_FEATURE_MISSING.name(), child.name() + " is mandatory under " + parent.name() + ".",
                List.of(child.id()), ValidationRelationDTO.fromDomain(relation), "Enable " + child.name() + ".");
    }

    private List<ValidationViolationDTO> constraintViolations(FeatureModel model, Set<String> selectedKnownIds) {
        return model.constraints().stream().filter(constraint -> constraint.isRequires() || constraint.isExcludes()).flatMap(constraint -> {
            if (constraint.isRequires() && selectedKnownIds.contains(constraint.source()) && !selectedKnownIds.contains(constraint.target())) {
                return java.util.stream.Stream.of(new ValidationViolationDTO(ValidationCode.REQUIRES_CONSTRAINT_VIOLATED.name(),
                        "Feature '" + constraint.source() + "' requires feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                        "Enable '" + constraint.target() + "' or disable '" + constraint.source() + "'."));
            }
            if (constraint.isExcludes() && selectedKnownIds.contains(constraint.source()) && selectedKnownIds.contains(constraint.target())) {
                return java.util.stream.Stream.of(new ValidationViolationDTO(ValidationCode.EXCLUDES_CONSTRAINT_VIOLATED.name(),
                        "Feature '" + constraint.source() + "' excludes feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                        "Disable either '" + constraint.source() + "' or '" + constraint.target() + "'."));
            }
            return java.util.stream.Stream.empty();
        }).toList();
    }

    private List<ValidationWarningDTO> expressionWarnings(FeatureModel model) {
        return model.constraints().stream().filter(FeatureConstraint::isExpression)
                .map(constraint -> new ValidationWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(),
                        "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.", relatedFeatureIds(constraint), constraint.id(),
                        "Review this constraint manually."))
                .toList();
    }

    private List<String> relatedFeatureIds(FeatureConstraint constraint) {
        return java.util.stream.Stream.of(constraint.source(), constraint.target()).filter(java.util.Objects::nonNull).toList();
    }
}
