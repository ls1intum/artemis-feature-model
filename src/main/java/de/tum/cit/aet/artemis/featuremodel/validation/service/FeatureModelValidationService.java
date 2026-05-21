package de.tum.cit.aet.artemis.featuremodel.validation.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FeatureModelValidationService.class);

    private final FeatureModelCatalogService catalogService;

    private final FeatureModelTreeService treeService;

    /**
     * Creates the feature model validation service.
     *
     * @param catalogService catalog service used to load the active model.
     * @param treeService tree service used to derive stable selection order.
     */
    public FeatureModelValidationService(FeatureModelCatalogService catalogService, FeatureModelTreeService treeService) {
        this.catalogService = catalogService;
        this.treeService = treeService;
    }

    /**
     * Validates a selection request against the active model.
     *
     * @param request validation request.
     * @return validation result.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the active model is structurally invalid.
     */
    public ValidationResultDTO validateSelection(ValidationRequest request) {
        FeatureModel model = catalogService.loadActiveModel();
        return validateSelection(model, request);
    }

    /**
     * Validates a transient client selection against the supplied model. Synthetic models use this entry point in tests.
     *
     * @param model model to validate against.
     * @param request validation request.
     * @return validation result.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the supplied model is structurally invalid.
     */
    public ValidationResultDTO validateSelection(FeatureModel model, ValidationRequest request) {
        log.debug("Validating feature selection with {} submitted feature ids against model '{}'.", request.selectedFeatureIds().size(), model.model().name());

        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        NormalizedSelection normalizedSelection = normalizeSelection(model, request, featuresById);
        Set<String> selectedKnownIds = Set.copyOf(normalizedSelection.selectedFeatureIds());

        List<ValidationViolationDTO> violations = validationViolations(model, request, featuresById, selectedKnownIds);
        List<ValidationWarningDTO> warnings = expressionWarnings(model);
        log.info("Validated feature selection for model '{}': valid={}, submitted={}, normalized={}, violations={}, warnings={}.", model.model().name(),
                violations.isEmpty(), request.selectedFeatureIds().size(), normalizedSelection.selectedFeatureIds().size(), violations.size(), warnings.size());
        return new ValidationResultDTO(violations.isEmpty(), normalizedSelection.selectedFeatureIds(), violations, warnings);
    }

    /**
     * Normalizes submitted selected feature ids to known ids in stable tree order.
     *
     * @param model model used to derive tree order.
     * @param request validation request.
     * @param featuresById known features keyed by id.
     * @return normalized selection.
     */
    private NormalizedSelection normalizeSelection(FeatureModel model, ValidationRequest request, Map<String, FeatureNode> featuresById) {
        Set<String> submittedIds = new LinkedHashSet<>(request.selectedFeatureIds());
        List<String> normalizedSelection = new ArrayList<>();

        addKnownIdsInTreeOrder(model, submittedIds, normalizedSelection);
        addKnownIdsMissingFromTree(submittedIds, featuresById, normalizedSelection);

        return new NormalizedSelection(normalizedSelection, submittedIds);
    }

    /**
     * Adds submitted ids that are known and present in the derived tree.
     *
     * @param model model used to derive tree order.
     * @param submittedIds unique submitted ids.
     * @param normalizedSelection mutable normalized selection accumulator.
     */
    private void addKnownIdsInTreeOrder(FeatureModel model, Set<String> submittedIds, List<String> normalizedSelection) {
        for (String featureId : treeService.treeOrderedFeatureIds(model)) {
            if (submittedIds.contains(featureId)) {
                normalizedSelection.add(featureId);
            }
        }
    }

    /**
     * Adds known submitted ids that were not present in the tree order.
     *
     * @param submittedIds unique submitted ids.
     * @param featuresById known features keyed by id.
     * @param normalizedSelection mutable normalized selection accumulator.
     */
    private void addKnownIdsMissingFromTree(Set<String> submittedIds, Map<String, FeatureNode> featuresById, List<String> normalizedSelection) {
        for (String submittedId : submittedIds) {
            if (featuresById.containsKey(submittedId) && !normalizedSelection.contains(submittedId)) {
                normalizedSelection.add(submittedId);
            }
        }
    }

    /**
     * Collects all validation violations for a submitted selection.
     *
     * @param model model to validate against.
     * @param request validation request.
     * @param featuresById known features keyed by id.
     * @param selectedKnownIds normalized known selected ids.
     * @return validation violations.
     */
    private List<ValidationViolationDTO> validationViolations(FeatureModel model, ValidationRequest request, Map<String, FeatureNode> featuresById,
            Set<String> selectedKnownIds) {
        List<ValidationViolationDTO> violations = new ArrayList<>();
        violations.addAll(unknownFeatureViolations(request, featuresById));
        violations.addAll(mandatoryRelationViolations(model, featuresById, selectedKnownIds));
        violations.addAll(constraintViolations(model, selectedKnownIds));
        return List.copyOf(violations);
    }

    /**
     * Reports unknown submitted feature ids.
     *
     * @param request validation request.
     * @param featuresById known features keyed by id.
     * @return unknown feature violations.
     */
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

    /**
     * Creates a violation for one unknown feature id.
     *
     * @param featureId unknown feature id.
     * @return validation violation.
     */
    private ValidationViolationDTO unknownFeatureViolation(String featureId) {
        String message = "Selected feature '" + featureId + "' does not exist.";
        String suggestion = "Remove '" + featureId + "' from the selection.";

        return new ValidationViolationDTO(ValidationCode.UNKNOWN_SELECTED_FEATURE.name(), message, List.of(featureId), null, suggestion);
    }

    /**
     * Reports mandatory child features missing below active parent paths.
     *
     * @param model model to validate against.
     * @param featuresById known features keyed by id.
     * @param selectedKnownIds normalized known selected ids.
     * @return mandatory relation violations.
     */
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

    /**
     * Computes feature ids whose paths are active for hierarchy validation.
     *
     * @param model model to inspect.
     * @param selectedKnownIds normalized known selected ids.
     * @return active feature ids.
     */
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

    /**
     * Checks whether a mandatory relation points to an unselected selectable child.
     *
     * @param relation relation to inspect.
     * @param activeFeatureIds active feature ids.
     * @param featuresById known features keyed by id.
     * @param selectedKnownIds normalized known selected ids.
     * @return true if the mandatory child is missing.
     */
    private boolean isMissingMandatoryChild(FeatureRelation relation, Set<String> activeFeatureIds, Map<String, FeatureNode> featuresById,
            Set<String> selectedKnownIds) {
        FeatureNode child = featuresById.get(relation.childId());
        boolean parentIsActive = activeFeatureIds.contains(relation.parentId());
        boolean childIsMissing = !selectedKnownIds.contains(relation.childId());
        return relation.isMandatory() && parentIsActive && isSelectableModule(child) && childIsMissing;
    }

    /**
     * Checks whether a feature is selectable by the user.
     *
     * @param feature feature to inspect.
     * @return true if the feature exists and is selectable.
     */
    private boolean isSelectableModule(FeatureNode feature) {
        return feature != null && feature.selectable();
    }

    /**
     * Creates a mandatory feature violation.
     *
     * @param relation mandatory relation.
     * @param child missing child feature.
     * @param parent active parent feature.
     * @return validation violation.
     */
    private ValidationViolationDTO mandatoryViolation(FeatureRelation relation, FeatureNode child, FeatureNode parent) {
        String message = child.name() + " is mandatory under " + parent.name() + ".";
        String suggestion = "Enable " + child.name() + ".";

        ValidationRelationDTO relationDTO = ValidationRelationDTO.fromDomain(relation);
        return new ValidationViolationDTO(ValidationCode.MANDATORY_FEATURE_MISSING.name(), message, List.of(child.id()), relationDTO, suggestion);
    }

    /**
     * Reports violated requires and excludes constraints.
     *
     * @param model model to validate against.
     * @param selectedKnownIds normalized known selected ids.
     * @return constraint violations.
     */
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

    /**
     * Checks whether a requires constraint is violated by the selection.
     *
     * @param constraint constraint to inspect.
     * @param selectedKnownIds normalized known selected ids.
     * @return true if the constraint is violated.
     */
    private boolean isRequiresConstraintViolated(FeatureConstraint constraint, Set<String> selectedKnownIds) {
        return constraint.isRequires() && selectedKnownIds.contains(constraint.source()) && !selectedKnownIds.contains(constraint.target());
    }

    /**
     * Checks whether an excludes constraint is violated by the selection.
     *
     * @param constraint constraint to inspect.
     * @param selectedKnownIds normalized known selected ids.
     * @return true if the constraint is violated.
     */
    private boolean isExcludesConstraintViolated(FeatureConstraint constraint, Set<String> selectedKnownIds) {
        return constraint.isExcludes() && selectedKnownIds.contains(constraint.source()) && selectedKnownIds.contains(constraint.target());
    }

    /**
     * Creates a requires constraint violation.
     *
     * @param constraint violated requires constraint.
     * @return validation violation.
     */
    private ValidationViolationDTO requiresConstraintViolation(FeatureConstraint constraint) {
        return new ValidationViolationDTO(ValidationCode.REQUIRES_CONSTRAINT_VIOLATED.name(),
                "Feature '" + constraint.source() + "' requires feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                "Enable '" + constraint.target() + "' or disable '" + constraint.source() + "'.");
    }

    /**
     * Creates an excludes constraint violation.
     *
     * @param constraint violated excludes constraint.
     * @return validation violation.
     */
    private ValidationViolationDTO excludesConstraintViolation(FeatureConstraint constraint) {
        return new ValidationViolationDTO(ValidationCode.EXCLUDES_CONSTRAINT_VIOLATED.name(),
                "Feature '" + constraint.source() + "' excludes feature '" + constraint.target() + "'.", relatedFeatureIds(constraint), null,
                "Disable either '" + constraint.source() + "' or '" + constraint.target() + "'.");
    }

    /**
     * Reports unsupported expression constraints as warnings.
     *
     * @param model model to inspect.
     * @return validation warnings.
     */
    private List<ValidationWarningDTO> expressionWarnings(FeatureModel model) {
        List<ValidationWarningDTO> warnings = new ArrayList<>();
        for (FeatureConstraint constraint : model.constraints()) {
            if (constraint.isExpression()) {
                warnings.add(expressionConstraintWarning(constraint));
            }
        }
        return List.copyOf(warnings);
    }

    /**
     * Creates a warning for an unsupported expression constraint.
     *
     * @param constraint expression constraint.
     * @return validation warning.
     */
    private ValidationWarningDTO expressionConstraintWarning(FeatureConstraint constraint) {
        String message = "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.";
        String suggestion = "Review this constraint manually.";
        List<String> featureIds = relatedFeatureIds(constraint);
        return new ValidationWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(), message, featureIds, constraint.id(), suggestion);
    }

    /**
     * Collects non-null source and target feature ids from a constraint.
     *
     * @param constraint constraint to inspect.
     * @return related feature ids.
     */
    private List<String> relatedFeatureIds(FeatureConstraint constraint) {
        return java.util.stream.Stream.of(constraint.source(), constraint.target()).filter(Objects::nonNull).toList();
    }
}
