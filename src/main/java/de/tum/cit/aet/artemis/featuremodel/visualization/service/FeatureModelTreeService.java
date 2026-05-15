package de.tum.cit.aet.artemis.featuremodel.visualization.service;

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
        Map<String, List<FeatureRelation>> relationsByParentId = model.relations().stream()
                .collect(Collectors.groupingBy(FeatureRelation::parentId, Collectors.collectingAndThen(Collectors.toList(), this::sortRelations)));

        FeatureNode root = model.features().stream().filter(FeatureNode::isRoot).findFirst().orElseThrow();
        return buildNode(root, null, featuresById, relationsByParentId);
    }

    public List<String> treeOrderedFeatureIds(FeatureModel model) {
        return flattenIds(buildTree(model));
    }

    private FeatureTreeNodeDTO buildNode(FeatureNode feature, FeatureRelation incomingRelation, Map<String, FeatureNode> featuresById,
            Map<String, List<FeatureRelation>> relationsByParentId) {
        List<FeatureTreeNodeDTO> children = relationsByParentId.getOrDefault(feature.id(), List.of()).stream()
                .map(relation -> buildNode(featuresById.get(relation.childId()), relation, featuresById, relationsByParentId)).toList();
        IncomingRelationDTO incomingRelationDTO = incomingRelation == null ? null : IncomingRelationDTO.fromDomain(incomingRelation);
        return new FeatureTreeNodeDTO(FeatureDTO.fromDomain(feature), incomingRelationDTO, children);
    }

    private List<FeatureRelation> sortRelations(List<FeatureRelation> relations) {
        return relations.stream().sorted(Comparator.comparingInt(FeatureRelation::order).thenComparing(FeatureRelation::childId)).toList();
    }

    private List<String> flattenIds(FeatureTreeNodeDTO node) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(node.feature().id()), node.children().stream().flatMap(child -> flattenIds(child).stream())).toList();
    }
}
