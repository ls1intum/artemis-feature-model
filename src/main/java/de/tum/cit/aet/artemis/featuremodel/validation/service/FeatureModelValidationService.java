package de.tum.cit.aet.artemis.featuremodel.validation.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Validates a transient client selection against the supplied model. Synthetic models use this entry point in tests.
     */
    public ValidationResultDTO validateSelection(FeatureModel model, ValidationRequest request) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        NormalizedSelection normalizedSelection = normalizeSelection(model, request, featuresById);
        Set<String> selectedKnownIds = Set.copyOf(normalizedSelection.selectedFeatureIds());

        List<ValidationViolationDTO> violations = validationViolations(model, request, featuresById, selectedKnownIds);
        List<ValidationWarningDTO> warnings = expressionWarnings(model);
        return new ValidationResultDTO(violations.isEmpty(), normalizedSelection.selectedFeatureIds(), violations, warnings);
    }

    private NormalizedSelection normalizeSelection(FeatureModel model, ValidationRequest request, Map<String, FeatureNode> featuresById) {
        Set<String> submittedIds = new LinkedHashSet<>(request.selectedFeatureIds());
        List<String> normalizedSelection = new ArrayList<>();

        addKnownIdsInTreeOrder(model, submittedIds, normalizedSelection);
        addKnownIdsMissingFromTree(submittedIds, featuresById, normalizedSelection);

        return new NormalizedSelection(normalizedSelection, submittedIds);
    }

    private void addKnownIdsInTreeOrder(FeatureModel model, Set<String> submittedIds, List<String> normalizedSelection) {
        for (String featureId : treeService.treeOrderedFeatureIds(model)) {
            if (submittedIds.contains(featureId)) {
                normalizedSelection.add(featureId);
            }
        }
    }

    private void addKnownIdsMissingFromTree(Set<String> submittedIds, Map<String, FeatureNode> featuresById, List<String> normalizedSelection) {
        for (String submittedId : submittedIds) {
            if (featuresById.containsKey(submittedId) && !normalizedSelection.contains(submittedId)) {
                normalizedSelection.add(submittedId);
            }
        }
    }

    private List<ValidationViolationDTO> validationViolations(FeatureModel model, ValidationRequest request, Map<String, FeatureNode> featuresById,
            Set<String> selectedKnownIds) {
        List<ValidationViolationDTO> violations = new ArrayList<>();
        violations.addAll(unknownFeatureViolations(request, featuresById));
        violations.addAll(mandatoryRelationViolations(model, featuresById, selectedKnownIds));
        violations.addAll(constraintViolations(model, selectedKnownIds));
        return List.copyOf(violations);
    }

    private List<ValidationViolationDTO> unknownFeatureViolations(ValidationRequest request, Map<String, FeatureNode> featuresById) {
        List<ValidationViolationDTO> violations = new ArrayList<>();
        Set<String> alreadyReportedIds = new LinkedHashSet<>();

        for (String submittedId : request.selectedFeatureIds()) {
            if (!featuresById.containsKey(submittedId) && alreadyReportedIds.add(submittedId)) {
                violations.add(unknownFeatureViolation(submittedId));
            }
        }

        return List.copyOf(violations);
    }

    private ValidationViolationDTO unknownFeatureViolation(String featureId) {
        String message = "Selected feature '" + featureId + "' does not exist.";
        String suggestion = "Remove '" + featureId + "' from the selection.";

        return new ValidationViolationDTO(ValidationCode.UNKNOWN_SELECTED_FEATURE.name(), message, List.of(featureId), null, suggestion);
    }

    private List<ValidationViolationDTO> mandatoryRelationViolations(FeatureModel model, Map<String, FeatureNode> featuresById,
            Set<String> selectedKnownIds) {
        Set<String> activeFeatureIds = activeFeatureIds(model, selectedKnownIds);
        List<ValidationViolationDTO> violations = new ArrayList<>();

        for (FeatureRelation relation : model.relations()) {
            if (isMissingMandatoryChild(relation, activeFeatureIds, featuresById, selectedKnownIds)) {
                FeatureNode child = featuresById.get(relation.childId());
                FeatureNode parent = featuresById.get(relation.parentId());
                violations.add(mandatoryViolation(relation, child, parent));
            }
        }

        return List.copyOf(violations);
    }

    private Set<String> activeFeatureIds(FeatureModel model, Set<String> selectedKnownIds) {
        Set<String> activeFeatureIds = new java.util.HashSet<>();
        for (FeatureNode feature : model.features()) {
            // Root and group nodes are structural paths and are active even when the client cannot toggle them.
            if (feature.isRoot() || feature.isGroup() || selectedKnownIds.contains(feature.id())) {
                activeFeatureIds.add(feature.id());
            }
        }
        return Set.copyOf(activeFeatureIds);
    }

    private boolean isMissingMandatoryChild(FeatureRelation relation, Set<String> activeFeatureIds, Map<String, FeatureNode> featuresById,
            Set<String> selectedKnownIds) {
        FeatureNode child = featuresById.get(relation.childId());
        boolean parentIsActive = activeFeatureIds.contains(relation.parentId());
        boolean childIsMissing = !selectedKnownIds.contains(relation.childId());
        return relation.isMandatory() && parentIsActive && isSelectableModule(child) && childIsMissing;
    }

    private boolean isSelectableModule(FeatureNode feature) {
        return feature != null && feature.selectable();
    }

    private ValidationViolationDTO mandatoryViolation(FeatureRelation relation, FeatureNode child, FeatureNode parent) {
        String message = child.name() + " is mandatory under " + parent.name() + ".";
        String suggestion = "Enable " + child.name() + ".";

        ValidationRelationDTO relationDTO = ValidationRelationDTO.fromDomain(relation);
        return new ValidationViolationDTO(ValidationCode.MANDATORY_FEATURE_MISSING.name(), message, List.of(child.id()), relationDTO, suggestion);
    }

    private List<ValidationViolationDTO> constraintViolations(FeatureModel model, Set<String> selectedKnownIds) {
        List<ValidationViolationDTO> violations = new ArrayList<>();
        for (FeatureConstraint constraint : model.constraints()) {
            if (isRequiresConstraintViolated(constraint, selectedKnownIds)) {
                violations.add(requiresConstraintViolation(constraint));
            }
            if (isExcludesConstraintViolated(constraint, selectedKnownIds)) {
                violations.add(excludesConstraintViolation(constraint));
            }
        }
        return List.copyOf(violations);
    }

    private boolean isRequiresConstraintViolated(FeatureConstraint constraint, Set<String> selectedKnownIds) {
        return constraint.isRequires() && selectedKnownIds.contains(constraint.source()) && !selectedKnownIds.contains(constraint.target());
    }

    private boolean isExcludesConstraintViolated(FeatureConstraint constraint, Set<String> selectedKnownIds) {
        return constraint.isExcludes() && selectedKnownIds.contains(constraint.source()) && selectedKnownIds.contains(constraint.target());
    }

    private ValidationViolationDTO requiresConstraintViolation(FeatureConstraint constraint) {
        return new ValidationViolationDTO(ValidationCode.REQUIRES_CONSTRAINT_VIOLATED.name(),
                "Feature '" + constraint.source() + "' requires feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                "Enable '" + constraint.target() + "' or disable '" + constraint.source() + "'.");
    }

    private ValidationViolationDTO excludesConstraintViolation(FeatureConstraint constraint) {
        return new ValidationViolationDTO(ValidationCode.EXCLUDES_CONSTRAINT_VIOLATED.name(),
                "Feature '" + constraint.source() + "' excludes feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                "Disable either '" + constraint.source() + "' or '" + constraint.target() + "'.");
    }

    private List<ValidationWarningDTO> expressionWarnings(FeatureModel model) {
        List<ValidationWarningDTO> warnings = new ArrayList<>();
        for (FeatureConstraint constraint : model.constraints()) {
            if (constraint.isExpression()) {
                warnings.add(expressionConstraintWarning(constraint));
            }
        }
        return List.copyOf(warnings);
    }

    private ValidationWarningDTO expressionConstraintWarning(FeatureConstraint constraint) {
        String message = "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.";
        String suggestion = "Review this constraint manually.";
        List<String> featureIds = relatedFeatureIds(constraint);
        return new ValidationWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(), message, featureIds, constraint.id(), suggestion);
    }

    private List<String> relatedFeatureIds(FeatureConstraint constraint) {
        return java.util.stream.Stream.of(constraint.source(), constraint.target()).filter(Objects::nonNull).toList();
    }
}
