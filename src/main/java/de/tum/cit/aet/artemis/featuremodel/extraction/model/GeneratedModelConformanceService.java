package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ArtifactMappingTargets;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/** Compares the assembled model with the complete resolved manifest contract independently of structural integrity. */
class GeneratedModelConformanceService {

    private static final String KIND_ROOT = "root";

    private static final String KIND_GROUP = "group";

    private static final String KIND_MODULE = "module";

    private static final String CATEGORY_DERIVED = "derived";

    private static final String CATEGORY_FUNCTIONAL = FeatureScopeManifest.CATEGORY_FUNCTIONAL;

    private static final String STATUS_GENERATED = "generated";

    private final ObjectMapper objectMapper;

    /**
     * Creates the semantic comparator.
     *
     * @param objectMapper mapper used to normalize manifest mapping values to JSON values.
     */
    GeneratedModelConformanceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** One manifest-declared node before its expected model semantics are derived. */
    private record NodeContract(String id, String parentId, String kind, String optionality, String category, String groupType, Integer order,
            int declarationIndex, ConceptualNode conceptual, ResolvedFeatureScope included) {
    }

    /** Model fields whose values are directly determined by the resolved manifest and scanned candidate. */
    private record FeatureContract(String name, String description, String kind, boolean selectable, String category, String defaultState,
            List<String> requiresCapabilities, List<ArtifactMapping> artifactMappings) {
    }

    /**
     * Validates every generated semantic surface controlled by the resolved manifest.
     *
     * @param manifest parsed manifest used for generation.
     * @param includedFeatures resolved included feature semantics.
     * @param candidates scanned candidates used for derived defaults and mappings.
     * @param generatedModel assembled model to verify.
     * @param artemisCommit pinned Artemis commit.
     * @return deterministic blocking findings; empty means complete semantic conformance.
     */
    List<ReportItem> validate(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<FeatureCandidate> candidates,
            FeatureModel generatedModel, String artemisCommit) {
        List<ReportItem> findings = new ArrayList<>();
        Map<String, FeatureCandidate> candidatesById = indexCandidates(candidates);
        Map<String, NodeContract> expectedNodes = expectedNodes(manifest, includedFeatures, findings);

        validateModelIdentity(generatedModel, artemisCommit, findings);
        validateFeatures(expectedNodes, candidatesById, generatedModel.features(), findings);
        validateRelations(expectedNodes, generatedModel.relations(), findings);
        validateConstraints(manifest.constraints(), generatedModel.constraints(), findings);
        return List.copyOf(findings);
    }

    /**
     * Indexes scanned candidates without changing their deterministic first occurrence.
     *
     * @param candidates scanned candidates.
     * @return candidates keyed by extraction id.
     */
    private Map<String, FeatureCandidate> indexCandidates(List<FeatureCandidate> candidates) {
        Map<String, FeatureCandidate> indexed = new LinkedHashMap<>();
        candidates.forEach(candidate -> indexed.putIfAbsent(candidate.id(), candidate));
        return indexed;
    }

    /**
     * Builds the complete expected node universe in manifest declaration order.
     *
     * @param manifest parsed manifest.
     * @param includedFeatures resolved includes.
     * @param findings diagnostic sink.
     * @return expected nodes keyed by feature id.
     */
    private Map<String, NodeContract> expectedNodes(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures,
            List<ReportItem> findings) {
        Map<String, NodeContract> nodes = new LinkedHashMap<>();
        int declarationIndex = 0;
        for (ConceptualNode node : manifest.conceptualNodes()) {
            putExpectedNode(nodes, new NodeContract(node.id(), node.parent(), defaultKind(node.kind()), node.optionality(), node.category(), node.groupType(),
                    node.order(), declarationIndex++, node, null), findings);
        }
        for (ResolvedFeatureScope included : includedFeatures) {
            String parentId = included.group() == null ? included.parent() : included.group();
            putExpectedNode(nodes, new NodeContract(included.id(), parentId, defaultKind(included.kind()), included.optionality(), included.category(), null,
                    included.order(), declarationIndex++, null, included), findings);
        }
        return nodes;
    }

    /**
     * Adds an expected node while reporting identifier collisions.
     *
     * @param nodes expected node index.
     * @param node node to add.
     * @param findings diagnostic sink.
     */
    private void putExpectedNode(Map<String, NodeContract> nodes, NodeContract node, List<ReportItem> findings) {
        if (nodes.putIfAbsent(node.id(), node) != null) {
            mismatch(findings, node.id(), "Resolved manifest emits duplicate feature id '" + node.id() + "'.");
        }
    }

    /**
     * Verifies deterministic model identity fields.
     *
     * @param model generated model.
     * @param artemisCommit pinned Artemis commit.
     * @param findings diagnostic sink.
     */
    private void validateModelIdentity(FeatureModel model, String artemisCommit, List<ReportItem> findings) {
        if (model.model() == null) {
            mismatch(findings, "model", "Generated model metadata is missing.");
            return;
        }
        requireEqual(findings, "model", "id", model.model().id(), GeneratedModelAssembler.GENERATED_MODEL_ID);
        requireEqual(findings, "model", "version", model.model().version(), GeneratedModelAssembler.generatedVersion(artemisCommit));
        requireEqual(findings, "model", "status", model.model().status(), STATUS_GENERATED);
        requireEqual(findings, "model", "source commit", model.model().sourceCommitSha(), artemisCommit);
    }

    /**
     * Compares exact feature membership and manifest-controlled node semantics.
     *
     * @param expectedNodes expected nodes.
     * @param candidatesById scanned candidates.
     * @param actualFeatures emitted features.
     * @param findings diagnostic sink.
     */
    private void validateFeatures(Map<String, NodeContract> expectedNodes, Map<String, FeatureCandidate> candidatesById, List<FeatureNode> actualFeatures,
            List<ReportItem> findings) {
        Map<String, FeatureNode> actualById = new LinkedHashMap<>();
        for (FeatureNode feature : actualFeatures) {
            if (actualById.putIfAbsent(feature.id(), feature) != null) {
                mismatch(findings, feature.id(), "Generated model contains duplicate feature id '" + feature.id() + "'.");
            }
        }
        for (NodeContract node : expectedNodes.values()) {
            FeatureNode actual = actualById.remove(node.id());
            if (actual == null) {
                mismatch(findings, node.id(), "Generated model is missing manifest feature '" + node.id() + "'.");
                continue;
            }
            FeatureContract expected = expectedFeature(node, candidatesById);
            requireEqual(findings, node.id(), "name", actual.name(), expected.name());
            requireEqual(findings, node.id(), "description", actual.description(), expected.description());
            requireEqual(findings, node.id(), "kind", actual.kind(), expected.kind());
            requireEqual(findings, node.id(), "selectable state", actual.selectable(), expected.selectable());
            requireEqual(findings, node.id(), "category", actual.category(), expected.category());
            requireEqual(findings, node.id(), "default state", actual.defaultState(), expected.defaultState());
            requireEqual(findings, node.id(), "required capabilities", actual.requiresCapabilities(), expected.requiresCapabilities());
            requireEqual(findings, node.id(), "artifact mappings", actual.artifactMappings(), expected.artifactMappings());
        }
        actualById.keySet().forEach(id -> mismatch(findings, id, "Generated model contains undeclared feature '" + id + "'."));
    }

    /**
     * Derives one node's expected generated semantics.
     *
     * @param node resolved node contract.
     * @param candidatesById scanned candidates.
     * @return expected feature fields.
     */
    private FeatureContract expectedFeature(NodeContract node, Map<String, FeatureCandidate> candidatesById) {
        boolean structural = KIND_ROOT.equals(node.kind()) || KIND_GROUP.equals(node.kind());
        boolean selectable = !structural;
        String category = node.category() == null ? structural ? CATEGORY_DERIVED : CATEGORY_FUNCTIONAL : node.category();
        if (node.included() == null) {
            ConceptualNode conceptual = node.conceptual();
            String name = conceptual.name() == null ? node.id() : conceptual.name();
            return new FeatureContract(name, conceptual.description(), node.kind(), selectable, category,
                    structural ? "not_applicable" : "enabled", List.of(), List.of());
        }
        ResolvedFeatureScope included = node.included();
        FeatureCandidate candidate = candidatesById.get(included.candidateId());
        String name = included.name() != null ? included.name() : candidate != null && candidate.name() != null ? candidate.name() : node.id();
        String description = included.description() != null ? included.description() : candidate == null ? null : candidate.description();
        return new FeatureContract(name, description, node.kind(), selectable, category, expectedDefaultState(included, candidate),
                included.requiresCapabilities(), expectedMappings(included, candidate));
    }

    /**
     * Resolves the expected default state using manifest, scan, then optionality precedence.
     *
     * @param included resolved included feature.
     * @param candidate matching scanned candidate, or null.
     * @return expected default state.
     */
    private String expectedDefaultState(ResolvedFeatureScope included, FeatureCandidate candidate) {
        if (included.defaultState() != null) {
            return included.defaultState();
        }
        if (candidate != null && candidate.defaultValue() instanceof Boolean enabled) {
            return enabled ? "enabled" : "disabled";
        }
        return FeatureScopeManifest.OPTIONALITY_MANDATORY.equals(included.optionality()) ? "enabled" : "disabled";
    }

    /**
     * Derives the exact mapping list from the scanned toggle and manifest hints.
     *
     * @param included resolved included feature.
     * @param candidate matching scanned candidate, or null.
     * @return expected mappings in declared order.
     */
    private List<ArtifactMapping> expectedMappings(ResolvedFeatureScope included, FeatureCandidate candidate) {
        List<ArtifactMapping> mappings = new ArrayList<>();
        if (candidate != null && candidate.configKey() != null && FeatureCandidate.KIND_MODULE_FEATURE.equals(candidate.kind())) {
            mappings.add(new ArtifactMapping(ArtifactMappingTargets.OVERLAY_TARGET, candidate.configKey(), ArtifactMappingSource.SELECTION,
                    objectMapper.valueToTree(Boolean.TRUE), objectMapper.valueToTree(Boolean.FALSE), null));
        }
        for (MappingHint hint : included.artifactMappings()) {
            mappings.add(new ArtifactMapping(hint.target(), hint.path(), hint.source(), jsonValue(hint.valueWhenSelected()),
                    jsonValue(hint.valueWhenDeselected()), hint.secret()));
        }
        return List.copyOf(mappings);
    }

    /**
     * Normalizes one optional manifest mapping value to its model JSON representation.
     *
     * @param value manifest value, or null.
     * @return JSON value, or null.
     */
    private tools.jackson.databind.JsonNode jsonValue(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    /**
     * Compares the exact hierarchy, optionality, group semantics, and relation order.
     *
     * @param expectedNodes expected nodes.
     * @param actualRelations emitted relations.
     * @param findings diagnostic sink.
     */
    private void validateRelations(Map<String, NodeContract> expectedNodes, List<FeatureRelation> actualRelations, List<ReportItem> findings) {
        Map<String, FeatureRelation> expectedByChild = expectedRelations(expectedNodes);
        Map<String, FeatureRelation> actualByChild = new LinkedHashMap<>();
        for (FeatureRelation relation : actualRelations) {
            if (actualByChild.putIfAbsent(relation.childId(), relation) != null) {
                mismatch(findings, relation.childId(), "Generated model contains multiple incoming relations for '" + relation.childId() + "'.");
            }
        }
        for (Map.Entry<String, FeatureRelation> entry : expectedByChild.entrySet()) {
            FeatureRelation actual = actualByChild.remove(entry.getKey());
            if (actual == null) {
                mismatch(findings, entry.getKey(), "Generated model is missing the manifest hierarchy relation for '" + entry.getKey() + "'.");
            }
            else {
                requireEqual(findings, entry.getKey(), "hierarchy relation", actual, entry.getValue());
            }
        }
        actualByChild.keySet().forEach(id -> mismatch(findings, id, "Generated model contains an undeclared hierarchy relation for '" + id + "'."));
    }

    /**
     * Derives incoming relations from manifest placement and sibling order.
     *
     * @param nodes expected nodes.
     * @return expected relation keyed by child id.
     */
    private Map<String, FeatureRelation> expectedRelations(Map<String, NodeContract> nodes) {
        Map<String, List<NodeContract>> childrenByParent = new LinkedHashMap<>();
        for (NodeContract node : nodes.values()) {
            if (node.parentId() != null) {
                childrenByParent.computeIfAbsent(node.parentId(), unused -> new ArrayList<>()).add(node);
            }
        }
        Comparator<NodeContract> order = Comparator.comparingInt((NodeContract node) -> node.order() == null ? Integer.MAX_VALUE : node.order())
                .thenComparingInt(NodeContract::declarationIndex);
        childrenByParent.values().forEach(children -> children.sort(order));

        Map<String, FeatureRelation> relations = new LinkedHashMap<>();
        for (NodeContract node : nodes.values()) {
            if (node.parentId() == null) {
                continue;
            }
            List<NodeContract> siblings = childrenByParent.get(node.parentId());
            int relationOrder = node.order() == null ? siblings.indexOf(node) + 1 : node.order();
            if (KIND_GROUP.equals(node.kind())) {
                relations.put(node.id(), new FeatureRelation(node.parentId(), node.id(), "group",
                        node.groupType() == null ? "and" : node.groupType(), relationOrder));
            }
            else {
                String relationType = FeatureScopeManifest.OPTIONALITY_MANDATORY.equals(node.optionality()) ? "mandatory" : "optional";
                relations.put(node.id(), new FeatureRelation(node.parentId(), node.id(), relationType, null, relationOrder));
            }
        }
        return relations;
    }

    /**
     * Compares exact constraint membership and semantics.
     *
     * @param expectedEntries manifest constraints.
     * @param actualConstraints emitted constraints.
     * @param findings diagnostic sink.
     */
    private void validateConstraints(List<ConstraintEntry> expectedEntries, List<FeatureConstraint> actualConstraints, List<ReportItem> findings) {
        Map<String, FeatureConstraint> expectedById = new LinkedHashMap<>();
        for (ConstraintEntry entry : expectedEntries) {
            expectedById.put(entry.id(), new FeatureConstraint(entry.id(), entry.type(), entry.source(), entry.target(), null, entry.description()));
        }
        Map<String, FeatureConstraint> actualById = new LinkedHashMap<>();
        for (FeatureConstraint constraint : actualConstraints) {
            if (actualById.putIfAbsent(constraint.id(), constraint) != null) {
                mismatch(findings, constraint.id(), "Generated model contains duplicate constraint id '" + constraint.id() + "'.");
            }
        }
        for (Map.Entry<String, FeatureConstraint> entry : expectedById.entrySet()) {
            FeatureConstraint actual = actualById.remove(entry.getKey());
            if (actual == null) {
                mismatch(findings, entry.getKey(), "Generated model is missing manifest constraint '" + entry.getKey() + "'.");
            }
            else {
                requireEqual(findings, entry.getKey(), "constraint semantics", actual, entry.getValue());
            }
        }
        actualById.keySet().forEach(id -> mismatch(findings, id, "Generated model contains undeclared constraint '" + id + "'."));
    }

    /**
     * Applies the manifest's default model kind.
     *
     * @param kind declared kind, or null.
     * @return explicit expected kind.
     */
    private String defaultKind(String kind) {
        return kind == null ? KIND_MODULE : kind;
    }

    /**
     * Records a mismatch when two semantic values differ.
     *
     * @param findings diagnostic sink.
     * @param subject feature, relation, constraint, or model identity.
     * @param field compared field.
     * @param actual generated value.
     * @param expected resolved manifest value.
     */
    private void requireEqual(List<ReportItem> findings, String subject, String field, Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            mismatch(findings, subject, "Generated " + field + " differs from the resolved manifest: expected=" + expected + ", actual=" + actual + ".");
        }
    }

    /**
     * Appends one stable blocking conformance diagnostic.
     *
     * @param findings diagnostic sink.
     * @param subject finding subject.
     * @param message finding detail.
     */
    private void mismatch(List<ReportItem> findings, String subject, String message) {
        findings.add(ReportItem.error(ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH, subject, message));
    }
}
