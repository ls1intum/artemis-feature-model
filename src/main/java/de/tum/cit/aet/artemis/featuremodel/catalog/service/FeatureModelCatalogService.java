package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final FeatureModelStore featureModelStore;

    private final FeatureModelIntegrityService integrityService;

    private final FeatureModelTreeService treeService;

    public FeatureModelCatalogService(FeatureModelStore featureModelStore, FeatureModelIntegrityService integrityService,
            FeatureModelTreeService treeService) {
        this.featureModelStore = featureModelStore;
        this.integrityService = integrityService;
        this.treeService = treeService;
    }

    public FeatureModel loadActiveModel() {
        FeatureModel model = featureModelStore.loadActiveModel();
        integrityService.validate(model);
        return model;
    }

    /**
     * Builds the complete REST response from the canonical domain model and derived read models.
     */
    public FeatureModelResponse getActiveFeatureModelResponse() {
        FeatureModel model = loadActiveModel();
        FeatureTreeNodeDTO tree = treeService.buildTree(model);
        List<FeatureDTO> features = featureDTOs(model);
        List<RelationDTO> relations = relationDTOs(model);
        List<ConstraintDTO> constraints = constraintDTOs(model);
        List<String> defaultSelectedFeatureIds = defaultSelectedFeatureIds(model);
        List<ModelWarningDTO> warnings = modelWarnings(model);

        ModelMetadataDTO modelMetadata = ModelMetadataDTO.fromDomain(model.model());
        return new FeatureModelResponse(modelMetadata, features, relations, constraints, tree, defaultSelectedFeatureIds, warnings);
    }

    /**
     * Derives the initial configurator selection from the source model instead of duplicating this logic in the frontend.
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

    public List<ModelWarningDTO> modelWarnings(FeatureModel model) {
        List<ModelWarningDTO> warnings = new ArrayList<>();
        for (FeatureConstraint constraint : model.constraints()) {
            if (constraint.isExpression()) {
                warnings.add(expressionConstraintWarning(constraint));
            }
        }
        return List.copyOf(warnings);
    }

    private List<FeatureDTO> featureDTOs(FeatureModel model) {
        return model.features().stream().map(FeatureDTO::fromDomain).toList();
    }

    private List<RelationDTO> relationDTOs(FeatureModel model) {
        return model.relations().stream().map(RelationDTO::fromDomain).toList();
    }

    private List<ConstraintDTO> constraintDTOs(FeatureModel model) {
        return model.constraints().stream().map(ConstraintDTO::fromDomain).toList();
    }

    private boolean isDefaultSelectedFeature(FeatureNode feature) {
        return feature != null && feature.selectable() && feature.isEnabledByDefault();
    }

    private ModelWarningDTO expressionConstraintWarning(FeatureConstraint constraint) {
        String message = "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.";
        return new ModelWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(), message, relatedFeatureIds(constraint), constraint.id());
    }

    private List<String> relatedFeatureIds(FeatureConstraint constraint) {
        return java.util.stream.Stream.of(constraint.source(), constraint.target()).filter(Objects::nonNull).toList();
    }
}
