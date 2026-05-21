package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.ConstraintDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureModelResponse;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.ModelMetadataDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.ModelWarningDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.RelationDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;
import de.tum.cit.aet.artemis.featuremodel.visualization.dto.FeatureTreeNodeDTO;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;

@Service
public class FeatureModelCatalogService {

    private static final Logger log = LoggerFactory.getLogger(FeatureModelCatalogService.class);

    private final FeatureModelStore featureModelStore;

    private final FeatureModelIntegrityService integrityService;

    private final FeatureModelTreeService treeService;

    /**
     * Creates the catalog service.
     *
     * @param featureModelStore store used to load the active model.
     * @param integrityService service used to validate model integrity.
     * @param treeService service used to build derived tree data.
     */
    public FeatureModelCatalogService(FeatureModelStore featureModelStore, FeatureModelIntegrityService integrityService,
            FeatureModelTreeService treeService) {
        this.featureModelStore = featureModelStore;
        this.integrityService = integrityService;
        this.treeService = treeService;
    }

    /**
     * Loads the active model and checks its integrity before use.
     *
     * @return active feature model.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the loaded model is structurally invalid.
     */
    public FeatureModel loadActiveModel() {
        FeatureModel model = featureModelStore.loadActiveModel();
        integrityService.validate(model);
        log.debug("Loaded and validated active feature model '{}' with {} features.", model.model().name(), model.features().size());
        return model;
    }

    /**
     * Builds the complete REST response from the canonical domain model and derived read models.
     *
     * @return active feature model API response.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the loaded model is structurally invalid.
     */
    public FeatureModelResponse getActiveFeatureModelResponse() {
        FeatureModel model = loadActiveModel();
        log.debug("Building active feature model transfer response for model '{}'.", model.model().name());

        FeatureTreeNodeDTO tree = treeService.buildTree(model);
        List<FeatureDTO> features = featureDTOs(model);
        List<RelationDTO> relations = relationDTOs(model);
        List<ConstraintDTO> constraints = constraintDTOs(model);
        List<String> defaultSelectedFeatureIds = defaultSelectedFeatureIds(model);
        List<ModelWarningDTO> warnings = modelWarnings(model);

        ModelMetadataDTO modelMetadata = ModelMetadataDTO.fromDomain(model.model());
        log.info("Built active feature model transfer response for '{}' with {} features, {} relations, {} constraints, {} default selections, and {} warnings.",
                model.model().name(), features.size(), relations.size(), constraints.size(), defaultSelectedFeatureIds.size(), warnings.size());
        return new FeatureModelResponse(modelMetadata, features, relations, constraints, tree, defaultSelectedFeatureIds, warnings);
    }

    /**
     * Derives the initial configurator selection from the source model instead of duplicating this logic in the frontend.
     *
     * @param model model to inspect.
     * @return default selected feature ids in tree order.
     */
    public List<String> defaultSelectedFeatureIds(FeatureModel model) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        List<String> defaultSelectedFeatureIds = new ArrayList<>();

        for (String featureId : treeService.treeOrderedFeatureIds(model)) {
            FeatureNode feature = featuresById.get(featureId);
            if (isDefaultSelectedFeature(feature)) {
                defaultSelectedFeatureIds.add(feature.id());
            }
        }

        return List.copyOf(defaultSelectedFeatureIds);
    }

    /**
     * Derives model-level warnings from constraints that cannot be evaluated in this MVP phase.
     *
     * @param model model to inspect.
     * @return model warning DTOs.
     */
    public List<ModelWarningDTO> modelWarnings(FeatureModel model) {
        List<ModelWarningDTO> warnings = new ArrayList<>();
        for (FeatureConstraint constraint : model.constraints()) {
            if (constraint.isExpression()) {
                warnings.add(expressionConstraintWarning(constraint));
            }
        }
        return List.copyOf(warnings);
    }

    /**
     * Converts source feature nodes to REST DTOs.
     *
     * @param model model containing source feature nodes.
     * @return feature DTOs.
     */
    private List<FeatureDTO> featureDTOs(FeatureModel model) {
        return model.features().stream().map(FeatureDTO::fromDomain).toList();
    }

    /**
     * Converts source relations to REST DTOs.
     *
     * @param model model containing source relations.
     * @return relation DTOs.
     */
    private List<RelationDTO> relationDTOs(FeatureModel model) {
        return model.relations().stream().map(RelationDTO::fromDomain).toList();
    }

    /**
     * Converts source constraints to REST DTOs.
     *
     * @param model model containing source constraints.
     * @return constraint DTOs.
     */
    private List<ConstraintDTO> constraintDTOs(FeatureModel model) {
        return model.constraints().stream().map(ConstraintDTO::fromDomain).toList();
    }

    /**
     * Checks whether a feature is selectable and enabled by default.
     *
     * @param feature feature to inspect.
     * @return true if the feature belongs in the backend-derived default selection.
     */
    private boolean isDefaultSelectedFeature(FeatureNode feature) {
        return feature != null && feature.selectable() && feature.isEnabledByDefault();
    }

    /**
     * Creates a stable warning for an unsupported expression constraint.
     *
     * @param constraint expression constraint.
     * @return warning DTO.
     */
    private ModelWarningDTO expressionConstraintWarning(FeatureConstraint constraint) {
        String message = "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.";
        return new ModelWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(), message, relatedFeatureIds(constraint), constraint.id());
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
