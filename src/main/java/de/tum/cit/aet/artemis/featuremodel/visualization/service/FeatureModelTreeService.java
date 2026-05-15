package de.tum.cit.aet.artemis.featuremodel.visualization.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureDTO;
import de.tum.cit.aet.artemis.featuremodel.visualization.dto.FeatureTreeNodeDTO;
import de.tum.cit.aet.artemis.featuremodel.visualization.dto.IncomingRelationDTO;

@Service
public class FeatureModelTreeService {

    public FeatureTreeNodeDTO buildTree(FeatureModel model) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        Map<String, List<FeatureRelation>> relationsByParentId = relationsByParentId(model);

        FeatureNode root = model.features().stream().filter(FeatureNode::isRoot).findFirst().orElseThrow();
        return buildNode(root, null, featuresById, relationsByParentId);
    }

    /**
     * Returns feature ids in the same order as the tree view so selection responses are stable for clients.
     */
    public List<String> treeOrderedFeatureIds(FeatureModel model) {
        List<String> featureIds = new ArrayList<>();
        appendFeatureIds(buildTree(model), featureIds);
        return List.copyOf(featureIds);
    }

    private Map<String, List<FeatureRelation>> relationsByParentId(FeatureModel model) {
        return model.relations().stream()
                .collect(Collectors.groupingBy(FeatureRelation::parentId, Collectors.collectingAndThen(Collectors.toList(), this::sortRelations)));
    }

    private FeatureTreeNodeDTO buildNode(FeatureNode feature, FeatureRelation incomingRelation, Map<String, FeatureNode> featuresById,
            Map<String, List<FeatureRelation>> relationsByParentId) {
        List<FeatureTreeNodeDTO> children = new ArrayList<>();
        for (FeatureRelation childRelation : relationsByParentId.getOrDefault(feature.id(), List.of())) {
            FeatureNode childFeature = featuresById.get(childRelation.childId());
            children.add(buildNode(childFeature, childRelation, featuresById, relationsByParentId));
        }

        IncomingRelationDTO incomingRelationDTO = incomingRelation == null ? null : IncomingRelationDTO.fromDomain(incomingRelation);
        return new FeatureTreeNodeDTO(FeatureDTO.fromDomain(feature), incomingRelationDTO, children);
    }

    private List<FeatureRelation> sortRelations(List<FeatureRelation> relations) {
        return relations.stream().sorted(Comparator.comparingInt(FeatureRelation::order).thenComparing(FeatureRelation::childId)).toList();
    }

    private void appendFeatureIds(FeatureTreeNodeDTO node, List<String> featureIds) {
        featureIds.add(node.feature().id());
        for (FeatureTreeNodeDTO child : node.children()) {
            appendFeatureIds(child, featureIds);
        }
    }
}
