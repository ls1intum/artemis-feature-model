package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.List;
import java.util.Map;
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

    public FeatureModelCatalogService(FeatureModelStore featureModelStore, FeatureModelIntegrityService integrityService, FeatureModelTreeService treeService) {
        this.featureModelStore = featureModelStore;
        this.integrityService = integrityService;
        this.treeService = treeService;
    }

    public FeatureModel loadActiveModel() {
        FeatureModel model = featureModelStore.loadActiveModel();
        integrityService.validate(model);
        return model;
    }

    public FeatureModelResponse getActiveFeatureModelResponse() {
        FeatureModel model = loadActiveModel();
        FeatureTreeNodeDTO tree = treeService.buildTree(model);
        return new FeatureModelResponse(ModelMetadataDTO.fromDomain(model.model()), model.features().stream().map(FeatureDTO::fromDomain).toList(),
                model.relations().stream().map(RelationDTO::fromDomain).toList(), model.constraints().stream().map(ConstraintDTO::fromDomain).toList(), tree,
                defaultSelectedFeatureIds(model), modelWarnings(model));
    }

    public List<String> defaultSelectedFeatureIds(FeatureModel model) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        return treeService.treeOrderedFeatureIds(model).stream().map(featuresById::get).filter(feature -> feature != null && feature.selectable())
                .filter(feature -> "enabled".equals(feature.defaultState())).map(FeatureNode::id).toList();
    }

    public List<ModelWarningDTO> modelWarnings(FeatureModel model) {
        return model.constraints().stream().filter(FeatureConstraint::isExpression)
                .map(constraint -> new ModelWarningDTO(ValidationCode.UNSUPPORTED_EXPRESSION_CONSTRAINT.name(),
                        "Expression constraint '" + constraint.id() + "' is not evaluated by this MVP backend.", relatedFeatureIds(constraint), constraint.id()))
                .toList();
    }

    private List<String> relatedFeatureIds(FeatureConstraint constraint) {
        return java.util.stream.Stream.of(constraint.source(), constraint.target()).filter(java.util.Objects::nonNull).toList();
    }
}
