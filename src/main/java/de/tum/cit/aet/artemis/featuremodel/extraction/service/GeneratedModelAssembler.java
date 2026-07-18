package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ExtractionMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/**
 * Assembles the generated feature model from the manifest's include entries and conceptual nodes. The output satisfies
 * the same domain records and loader code path as the bundled curated model: hierarchy and relation orders come from
 * the manifest, names and descriptions from Artemis i18n with manifest overrides, kind-based category and role
 * defaults keep technical features maintainer-only, the enabled-key artifact mapping is auto-derived from the scanned
 * configuration key, declared mapping hints mirror the curated mapping shapes, and every anchored feature carries a
 * source block with merged evidence references. Output ordering is a deterministic depth-first traversal, so two runs
 * on the same commit produce byte-identical models.
 */
class GeneratedModelAssembler {

    static final String GENERATED_MODEL_ID = "artemis-generated-feature-model";

    static final String GENERATED_MODEL_NAME = "Artemis Generated Feature Model";

    static final String OVERLAY_TARGET = "application-feature-model.yml";

    private static final String KIND_ROOT = "root";

    private static final String KIND_GROUP = "group";

    private static final String KIND_MODULE = "module";

    private static final String CATEGORY_DERIVED = "derived";

    private static final String CATEGORY_FUNCTIONAL = FeatureScopeManifest.CATEGORY_FUNCTIONAL;

    private static final String CATEGORY_TECHNICAL = FeatureScopeManifest.CATEGORY_TECHNICAL;

    private static final String ROLE_TEACHER = "teacher";

    private static final String ROLE_MAINTAINER = "maintainer";

    private static final int SHORT_COMMIT_LENGTH = 12;

    private final ObjectMapper objectMapper;

    /**
     * Creates the assembler.
     *
     * @param objectMapper Jackson mapper used to convert declared mapping values into the model's JSON value type.
     */
    GeneratedModelAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Assembly result.
     *
     * @param model assembled generated feature model.
     * @param items assembly diagnostics.
     */
    record Result(FeatureModel model, List<ReportItem> items) {
    }

    /** One node of the generated hierarchy: either a conceptual manifest node or a resolved included candidate. */
    private record ModelNode(String id, String parentId, String kind, String optionality, String category, String groupType, Integer order,
            int declarationIndex, ConceptualNode conceptual, ResolvedFeatureScope included) {
    }

    /**
     * Assembles the generated model.
     *
     * @param manifest loaded scope manifest.
     * @param includedFeatures resolved include semantics from the curation step.
     * @param candidates extracted candidates providing names, config keys, and defaults.
     * @param evidence evidence items backing the candidates.
     * @param relationCandidates extracted relation candidates, checked against the declared constraints.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @return assembled model and diagnostics.
     */
    Result assemble(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<FeatureCandidate> candidates, List<EvidenceItem> evidence,
            List<RelationCandidate> relationCandidates, String artemisCommit) {
        List<ReportItem> items = new ArrayList<>();
        Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.id(), candidate));
        Map<String, List<EvidenceItem>> evidenceByCandidate = new LinkedHashMap<>();
        evidence.forEach(item -> evidenceByCandidate.computeIfAbsent(item.candidateId(), unused -> new ArrayList<>()).add(item));

        Map<String, ModelNode> nodesById = buildNodeUniverse(manifest, includedFeatures, items);
        ModelNode root = findRoot(nodesById, items);
        List<FeatureNode> features = new ArrayList<>();
        List<FeatureRelation> relations = new ArrayList<>();
        if (root != null) {
            Map<String, List<ModelNode>> childrenByParent = childrenByParent(nodesById);
            emitDepthFirst(root, null, childrenByParent, candidatesById, evidenceByCandidate, features, relations);
        }
        List<FeatureConstraint> constraints = assembleConstraints(manifest);
        reportUndeclaredRelationCandidates(manifest, includedFeatures, relationCandidates, items);

        ModelMetadata metadata = new ModelMetadata(GENERATED_MODEL_ID, GENERATED_MODEL_NAME, generatedVersion(artemisCommit), "generated", artemisCommit);
        return new Result(new FeatureModel(metadata, features, relations, constraints), List.copyOf(items));
    }

    /**
     * Derives the generated model version from the scanned commit, so a snapshot import can distinguish models of
     * different Artemis states.
     *
     * @param artemisCommit resolved commit of the scanned checkout.
     * @return generated model version.
     */
    static String generatedVersion(String artemisCommit) {
        String shortCommit = artemisCommit == null ? "unknown" : artemisCommit.substring(0, Math.min(SHORT_COMMIT_LENGTH, artemisCommit.length()));
        return "0.1.0+" + shortCommit;
    }

    /**
     * Builds the node universe from conceptual nodes and resolved includes, skipping includes whose placement does not
     * resolve; the curation step has already reported those conflicts.
     *
     * @param manifest loaded manifest.
     * @param includedFeatures resolved include semantics.
     * @param items diagnostics sink.
     * @return nodes keyed by curated id in declaration order.
     */
    private Map<String, ModelNode> buildNodeUniverse(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
        Map<String, ModelNode> nodesById = new LinkedHashMap<>();
        int declarationIndex = 0;
        for (ConceptualNode node : manifest.conceptualNodes()) {
            nodesById.put(node.id(), new ModelNode(node.id(), node.parent(), node.kind() == null ? KIND_MODULE : node.kind(), node.optionality(),
                    node.category(), node.groupType(), node.order(), declarationIndex++, node, null));
        }
        for (ResolvedFeatureScope included : includedFeatures) {
            String parentId = included.group() != null ? included.group() : included.parent();
            if (nodesById.containsKey(included.id())) {
                items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, included.id(),
                        "Included feature id '" + included.id() + "' collides with another generated node; the first declaration wins."));
                continue;
            }
            nodesById.put(included.id(), new ModelNode(included.id(), parentId, included.kind() == null ? KIND_MODULE : included.kind(),
                    included.optionality(), included.category(), null, included.order(), declarationIndex++, null, included));
        }
        return nodesById;
    }

    /**
     * Finds the single root node.
     *
     * @param nodesById node universe.
     * @param items diagnostics sink.
     * @return root node, or null when the manifest declares none.
     */
    private ModelNode findRoot(Map<String, ModelNode> nodesById, List<ReportItem> items) {
        for (ModelNode node : nodesById.values()) {
            if (KIND_ROOT.equals(node.kind())) {
                return node;
            }
        }
        items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, "generated-model",
                "The manifest declares no root conceptual node; the generated model is empty."));
        return null;
    }

    /**
     * Groups nodes by parent with deterministic sibling order: declared orders first, then declaration sequence.
     *
     * @param nodesById node universe.
     * @return children per parent id.
     */
    private Map<String, List<ModelNode>> childrenByParent(Map<String, ModelNode> nodesById) {
        Map<String, List<ModelNode>> childrenByParent = new LinkedHashMap<>();
        for (ModelNode node : nodesById.values()) {
            if (node.parentId() != null && nodesById.containsKey(node.parentId())) {
                childrenByParent.computeIfAbsent(node.parentId(), unused -> new ArrayList<>()).add(node);
            }
        }
        Comparator<ModelNode> siblingOrder = Comparator
                .comparingInt((ModelNode node) -> node.order() == null ? Integer.MAX_VALUE : node.order())
                .thenComparingInt(ModelNode::declarationIndex);
        childrenByParent.values().forEach(children -> children.sort(siblingOrder));
        return childrenByParent;
    }

    /**
     * Emits features and relations in depth-first order: each node emits its incoming relation and feature node, then
     * its children in sibling order, exactly mirroring the curated model's file layout.
     *
     * @param node current node.
     * @param parent parent node, or null for the root.
     * @param childrenByParent children per parent id.
     * @param candidatesById candidates keyed by candidate id.
     * @param evidenceByCandidate evidence per candidate id.
     * @param features feature sink.
     * @param relations relation sink.
     */
    private void emitDepthFirst(ModelNode node, ModelNode parent, Map<String, List<ModelNode>> childrenByParent, Map<String, FeatureCandidate> candidatesById,
            Map<String, List<EvidenceItem>> evidenceByCandidate, List<FeatureNode> features, List<FeatureRelation> relations) {
        if (parent != null) {
            relations.add(relation(parent, node, siblingPosition(node, childrenByParent.get(parent.id()))));
        }
        features.add(featureNode(node, candidatesById, evidenceByCandidate));
        for (ModelNode child : childrenByParent.getOrDefault(node.id(), List.of())) {
            emitDepthFirst(child, node, childrenByParent, candidatesById, evidenceByCandidate, features, relations);
        }
    }

    /**
     * Resolves the 1-based relation order of a node among its sorted siblings, preferring the declared order.
     *
     * @param node current node.
     * @param siblings sorted siblings including the node.
     * @return relation order.
     */
    private int siblingPosition(ModelNode node, List<ModelNode> siblings) {
        if (node.order() != null) {
            return node.order();
        }
        return siblings.indexOf(node) + 1;
    }

    /**
     * Builds the incoming relation of a node: group nodes get a group relation with their declared group type, other
     * nodes a mandatory or optional relation from their optionality.
     *
     * @param parent parent node.
     * @param node current node.
     * @param order relation order.
     * @return feature relation.
     */
    private FeatureRelation relation(ModelNode parent, ModelNode node, int order) {
        if (KIND_GROUP.equals(node.kind())) {
            String groupType = node.groupType() == null ? "and" : node.groupType();
            return new FeatureRelation(parent.id(), node.id(), "group", groupType, order);
        }
        String relationType = FeatureScopeManifest.OPTIONALITY_MANDATORY.equals(node.optionality()) ? "mandatory" : "optional";
        return new FeatureRelation(parent.id(), node.id(), relationType, null, order);
    }

    /**
     * Builds the feature node for a generated model node.
     *
     * @param node current node.
     * @param candidatesById candidates keyed by candidate id.
     * @param evidenceByCandidate evidence per candidate id.
     * @return feature node.
     */
    private FeatureNode featureNode(ModelNode node, Map<String, FeatureCandidate> candidatesById, Map<String, List<EvidenceItem>> evidenceByCandidate) {
        boolean structural = KIND_ROOT.equals(node.kind()) || KIND_GROUP.equals(node.kind());
        boolean selectable = !structural;
        String category = category(node, structural);
        boolean technical = CATEGORY_TECHNICAL.equals(category);
        List<String> visibleTo = technical ? List.of(ROLE_MAINTAINER) : List.of(ROLE_TEACHER, ROLE_MAINTAINER);
        List<String> configurableBy = !selectable ? List.of() : technical ? List.of(ROLE_MAINTAINER) : List.of(ROLE_TEACHER, ROLE_MAINTAINER);

        if (node.included() == null) {
            ConceptualNode conceptual = node.conceptual();
            String defaultState = structural ? "not_applicable" : "enabled";
            return new FeatureNode(node.id(), conceptual.name() != null ? conceptual.name() : node.id(), node.kind(), selectable, conceptual.description(),
                    defaultState, null, category, visibleTo, configurableBy, List.of(), List.of(),
                    new ExtractionMetadata("manual-curation", "high", "manually_confirmed"));
        }

        ResolvedFeatureScope included = node.included();
        FeatureCandidate candidate = candidatesById.get(included.candidateId());
        String name = included.name() != null ? included.name() : candidate != null && candidate.name() != null ? candidate.name() : node.id();
        String description = included.description() != null ? included.description() : candidate == null ? null : candidate.description();
        String defaultState = defaultState(included, candidate);
        FeatureSource source = source(candidate, evidenceByCandidate.getOrDefault(included.candidateId(), List.of()));
        List<ArtifactMapping> mappings = artifactMappings(included, candidate);
        String confidence = candidate != null && candidate.configKey() != null && candidate.backendConditionClass() != null ? "high" : "medium";
        return new FeatureNode(node.id(), name, node.kind(), selectable, description, defaultState, source, category, visibleTo, configurableBy,
                included.requiresCapabilities(), mappings, new ExtractionMetadata("automatic", confidence, "generated"));
    }

    /**
     * Resolves the category of a node: the declared category, otherwise derived for structural nodes and functional
     * for selectable nodes, mirroring the curated defaults.
     *
     * @param node current node.
     * @param structural whether the node is a root or group node.
     * @return category.
     */
    private String category(ModelNode node, boolean structural) {
        if (node.category() != null) {
            return node.category();
        }
        return structural ? CATEGORY_DERIVED : CATEGORY_FUNCTIONAL;
    }

    /**
     * Resolves the default state of an included feature: the declared state wins, then the scanned boolean YAML
     * default, then the optionality.
     *
     * @param included resolved include semantics.
     * @param candidate extracted candidate, or null.
     * @return default state.
     */
    private String defaultState(ResolvedFeatureScope included, FeatureCandidate candidate) {
        if (included.defaultState() != null) {
            return included.defaultState();
        }
        if (candidate != null && candidate.defaultValue() instanceof Boolean enabled) {
            return enabled ? "enabled" : "disabled";
        }
        return FeatureScopeManifest.OPTIONALITY_MANDATORY.equals(included.optionality()) ? "enabled" : "disabled";
    }

    /**
     * Builds the source block of an included feature from its candidate anchors and merged evidence references.
     *
     * @param candidate extracted candidate, or null.
     * @param evidence evidence items of the candidate.
     * @return source block, or null when the candidate is unknown.
     */
    private FeatureSource source(FeatureCandidate candidate, List<EvidenceItem> evidence) {
        if (candidate == null) {
            return null;
        }
        return new FeatureSource(candidate.configKey(), candidate.springProfile(), candidate.frontendConstant(), candidate.backendConditionClass(),
                evidenceReferences(evidence));
    }

    /**
     * Merges evidence items into deterministic references: items with lines become {@code FileName.ext:1,2} entries
     * merged per file name and sorted; items without a line keep their checkout-relative path. Only anchor-grade
     * evidence enters the model's source block; the broad usage evidence stays in {@code evidence.json} so the model
     * remains readable.
     *
     * @param evidence evidence items of one candidate.
     * @return sorted evidence references.
     */
    private List<String> evidenceReferences(List<EvidenceItem> evidence) {
        Map<String, Set<Integer>> linesByFileName = new TreeMap<>();
        Set<String> lineLessReferences = new LinkedHashSet<>();
        for (EvidenceItem item : evidence) {
            if (item.kind() != null && item.kind().startsWith("usage-")) {
                continue;
            }
            if (item.line() == null) {
                lineLessReferences.add(item.file());
                continue;
            }
            String fileName = item.file().substring(item.file().lastIndexOf('/') + 1);
            linesByFileName.computeIfAbsent(fileName, unused -> new TreeSet<>()).add(item.line());
        }
        List<String> references = new ArrayList<>();
        linesByFileName.forEach((fileName, lines) -> {
            StringBuilder reference = new StringBuilder(fileName).append(':');
            List<String> lineTexts = lines.stream().map(String::valueOf).toList();
            reference.append(String.join(",", lineTexts));
            references.add(reference.toString());
        });
        lineLessReferences.stream().sorted().forEach(references::add);
        return List.copyOf(references);
    }

    /**
     * Builds the artifact mappings of an included feature: the auto-derived enabled-key toggle mapping first, then
     * the declared hints in declaration order, mirroring the curated mapping layout.
     *
     * @param included resolved include semantics.
     * @param candidate extracted candidate, or null.
     * @return artifact mappings.
     */
    private List<ArtifactMapping> artifactMappings(ResolvedFeatureScope included, FeatureCandidate candidate) {
        List<ArtifactMapping> mappings = new ArrayList<>();
        if (candidate != null && candidate.configKey() != null && FeatureCandidate.KIND_MODULE_FEATURE.equals(candidate.kind())) {
            mappings.add(new ArtifactMapping(OVERLAY_TARGET, candidate.configKey(), objectMapper.valueToTree(Boolean.TRUE),
                    objectMapper.valueToTree(Boolean.FALSE), null, null, null));
        }
        for (MappingHint hint : included.artifactMappings()) {
            mappings.add(new ArtifactMapping(hint.target(), hint.path(), hint.valueWhenSelected() == null ? null : objectMapper.valueToTree(hint.valueWhenSelected()),
                    hint.valueWhenDeselected() == null ? null : objectMapper.valueToTree(hint.valueWhenDeselected()), hint.valueFromProfile(),
                    hint.requiredWhenSelected(), hint.secret()));
        }
        return List.copyOf(mappings);
    }

    /**
     * Converts the declared manifest constraints into model constraints.
     *
     * @param manifest loaded manifest.
     * @return model constraints in declaration order.
     */
    private List<FeatureConstraint> assembleConstraints(FeatureScopeManifest manifest) {
        List<FeatureConstraint> constraints = new ArrayList<>();
        for (ConstraintEntry entry : manifest.constraints()) {
            constraints.add(new FeatureConstraint(entry.id(), entry.type(), entry.source(), entry.target(), null, entry.description()));
        }
        return List.copyOf(constraints);
    }

    /**
     * Reports directed relation candidates whose endpoints are both included but which no declared constraint covers.
     * The composite condition is enforcement-eligible evidence; the info item connects it to the pending curation
     * decision without auto-admitting a constraint.
     *
     * @param manifest loaded manifest.
     * @param includedFeatures resolved include semantics.
     * @param relationCandidates extracted relation candidates.
     * @param items diagnostics sink.
     */
    private void reportUndeclaredRelationCandidates(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures,
            List<RelationCandidate> relationCandidates, List<ReportItem> items) {
        Map<String, String> includedIdByCandidate = new LinkedHashMap<>();
        includedFeatures.forEach(included -> includedIdByCandidate.put(included.candidateId(), included.id()));
        Set<String> declaredPairs = new LinkedHashSet<>();
        for (ConstraintEntry constraint : manifest.constraints()) {
            declaredPairs.add(constraint.source() + "->" + constraint.target());
            declaredPairs.add(constraint.target() + "->" + constraint.source());
        }
        for (RelationCandidate relationCandidate : relationCandidates) {
            if (!relationCandidate.directed() || relationCandidate.sourceCandidateId() == null) {
                continue;
            }
            String sourceId = includedIdByCandidate.get(relationCandidate.sourceCandidateId());
            if (sourceId == null) {
                continue;
            }
            for (String memberCandidateId : relationCandidate.memberCandidateIds()) {
                String targetId = includedIdByCandidate.get(memberCandidateId);
                if (targetId == null || targetId.equals(sourceId) || declaredPairs.contains(sourceId + "->" + targetId)) {
                    continue;
                }
                items.add(ReportItem.info(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED, relationCandidate.id(),
                        "Composite condition '" + relationCandidate.conditionClass() + "' relates included features '" + sourceId + "' and '" + targetId
                                + "' but the manifest declares no matching constraint."));
            }
        }
    }
}
