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

    /**
     * Builds a tree DTO from the flat source model.
     *
     * @param model model to transform.
     * @return root tree node.
     * @throws java.util.NoSuchElementException if the model does not contain a root feature.
     */
    public FeatureTreeNodeDTO buildTree(FeatureModel model) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        Map<String, List<FeatureRelation>> relationsByParentId = relationsByParentId(model);

        FeatureNode root = model.features().stream().filter(FeatureNode::isRoot).findFirst().orElseThrow();
        return buildNode(root, null, featuresById, relationsByParentId);
    }

    /**
     * Returns feature ids in the same order as the tree view so selection responses are stable for clients.
     *
     * @param model model to inspect.
     * @return feature ids in tree order.
     * @throws java.util.NoSuchElementException if the model does not contain a root feature.
     */
    public List<String> treeOrderedFeatureIds(FeatureModel model) {
        List<String> featureIds = new ArrayList<>();
        appendFeatureIds(buildTree(model), featureIds);
        return List.copyOf(featureIds);
    }

    /**
     * Groups source relations by parent id and sorts each child list.
     *
     * @param model model containing relations.
     * @return sorted relations keyed by parent feature id.
     */
    private Map<String, List<FeatureRelation>> relationsByParentId(FeatureModel model) {
        return model.relations().stream()
                .collect(Collectors.groupingBy(FeatureRelation::parentId, Collectors.collectingAndThen(Collectors.toList(), this::sortRelations)));
    }

    /**
     * Builds one tree node and its descendants.
     *
     * @param feature feature represented by the node.
     * @param incomingRelation incoming relation, or null for the root.
     * @param featuresById known features keyed by id.
     * @param relationsByParentId sorted relations keyed by parent id.
     * @return tree node DTO.
     */
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

    /**
     * Sorts relations by configured order and then by child id for deterministic output.
     *
     * @param relations relations to sort.
     * @return sorted relations.
     */
    private List<FeatureRelation> sortRelations(List<FeatureRelation> relations) {
        return relations.stream().sorted(Comparator.comparingInt(FeatureRelation::order).thenComparing(FeatureRelation::childId)).toList();
    }

    /**
     * Appends the ids of a node and all descendants to an accumulator.
     *
     * @param node tree node to traverse.
     * @param featureIds mutable feature id accumulator.
     */
    private void appendFeatureIds(FeatureTreeNodeDTO node, List<String> featureIds) {
        featureIds.add(node.feature().id());
        for (FeatureTreeNodeDTO child : node.children()) {
            appendFeatureIds(child, featureIds);
        }
    }
}
