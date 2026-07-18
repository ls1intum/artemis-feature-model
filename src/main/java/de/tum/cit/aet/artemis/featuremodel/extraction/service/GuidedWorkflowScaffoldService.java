package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Keeps the authored guided workflow structurally in sync with the manifest include set without ever touching prose.
 * The sync is an incremental diff, not a regeneration: covered features cause zero writes, a newly included functional
 * feature gains a stub option with filled wiring and TODO prose, an orphan reference is flagged but never deleted, and
 * a single id rename is rewritten mechanically across all id reference lists. A run without changes leaves the file
 * byte-identical, which is the idempotence contract of the {@code syncGuidedWorkflowScaffold} task.
 */
public class GuidedWorkflowScaffoldService {

    /** Sentinel prefix for scaffold-generated prose; the guided workflow diagnostics keep flagging it until replaced. */
    private static final String TODO_PROSE = "TODO: ";

    private final ObjectMapper objectMapper;

    /**
     * Creates the scaffold service.
     *
     * @param objectMapper Jackson mapper used to create the stub nodes.
     */
    public GuidedWorkflowScaffoldService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sync result.
     *
     * @param changed whether the workflow document was modified and must be rewritten.
     * @param workflow possibly modified workflow document.
     * @param report structural change and flag report.
     */
    public record Result(boolean changed, ObjectNode workflow, ScaffoldReport report) {
    }

    /**
     * Report of one scaffold sync run.
     *
     * @param status {@code no-changes} or {@code updated}.
     * @param addedOptionIds ids of appended stub options.
     * @param addedDecisionIds ids of appended scaffold decisions that need placement review.
     * @param addedReviewGroupNodeIds group node ids of appended review groups.
     * @param renamedIds renames applied as {@code old->new} entries.
     * @param orphanReferences feature ids referenced by the workflow that no manifest entry declares; never deleted.
     */
    public record ScaffoldReport(String status, List<String> addedOptionIds, List<String> addedDecisionIds, List<String> addedReviewGroupNodeIds,
            List<String> renamedIds, List<String> orphanReferences) {
    }

    /** One guided-eligible feature of the manifest with its group placement and display label. */
    private record EligibleFeature(String id, String group, String label) {
    }

    /**
     * Synchronizes the authored workflow document with the manifest.
     *
     * @param workflow parsed authored workflow document; mutated in place when changes are needed.
     * @param manifest loaded scope manifest.
     * @return sync result with the change report.
     */
    public Result sync(ObjectNode workflow, FeatureScopeManifest manifest) {
        Map<String, EligibleFeature> eligibleById = eligibleFeatures(manifest);
        Set<String> knownIds = knownIds(manifest);

        List<String> renames = applySingleRename(workflow, eligibleById, knownIds);
        Set<String> coveredIds = coveredFeatureIds(workflow);
        List<String> orphanReferences = referencedIds(workflow).stream().filter(id -> !knownIds.contains(id)).toList();

        Map<String, String> groupByFeatureId = new LinkedHashMap<>();
        eligibleById.values().forEach(feature -> groupByFeatureId.put(feature.id(), feature.group()));
        List<String> addedOptionIds = new ArrayList<>();
        List<String> addedDecisionIds = new ArrayList<>();
        List<String> addedReviewGroupNodeIds = new ArrayList<>();
        for (EligibleFeature feature : eligibleById.values()) {
            if (coveredIds.contains(feature.id())) {
                continue;
            }
            appendStubOption(workflow, feature, groupByFeatureId, addedOptionIds, addedDecisionIds);
            ensureReviewGroup(workflow, feature.group(), addedReviewGroupNodeIds);
        }

        boolean changed = !renames.isEmpty() || !addedOptionIds.isEmpty() || !addedReviewGroupNodeIds.isEmpty();
        String status = changed ? "updated" : "no-changes";
        return new Result(changed, workflow, new ScaffoldReport(status, List.copyOf(addedOptionIds), List.copyOf(addedDecisionIds),
                List.copyOf(addedReviewGroupNodeIds), renames, List.copyOf(orphanReferences)));
    }

    /**
     * Serializes a workflow document in the authored file style: two-space indentation for objects and arrays, a
     * space only after the field colon, collapsed empty arrays and objects, and a trailing line feed. A parse and
     * write round trip of the untouched authored resource is byte-identical, so a no-change sync never rewrites prose
     * even at the byte level.
     *
     * @param workflow workflow document.
     * @return serialized document text.
     */
    public String writeWorkflow(ObjectNode workflow) {
        Separators separators = Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER).withObjectEmptySeparator("")
                .withArrayEmptySeparator("");
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(separators).withObjectIndenter(indenter).withArrayIndenter(indenter);
        return objectMapper.writer().with(printer).writeValueAsString(workflow) + "\n";
    }

    /**
     * Collects the guided-eligible features: manifest includes and conceptual module nodes that are not technical.
     * Technical features are maintainer-only and never enter the guided teacher surface.
     *
     * @param manifest loaded manifest.
     * @return eligible features keyed by id in manifest order.
     */
    private Map<String, EligibleFeature> eligibleFeatures(FeatureScopeManifest manifest) {
        Map<String, EligibleFeature> eligibleById = new LinkedHashMap<>();
        for (IncludeEntry entry : manifest.include()) {
            if (FeatureScopeManifest.CATEGORY_TECHNICAL.equals(entry.category())) {
                continue;
            }
            String group = entry.group() != null ? entry.group() : entry.parent();
            eligibleById.put(entry.id(), new EligibleFeature(entry.id(), group, entry.name() != null ? entry.name() : titleCase(entry.id())));
        }
        for (ConceptualNode node : manifest.conceptualNodes()) {
            if (!"module".equals(node.kind()) || FeatureScopeManifest.CATEGORY_TECHNICAL.equals(node.category())) {
                continue;
            }
            eligibleById.put(node.id(), new EligibleFeature(node.id(), node.parent(), node.name() != null ? node.name() : titleCase(node.id())));
        }
        return eligibleById;
    }

    /**
     * Collects every feature id the manifest declares, included or conceptual, functional or technical.
     *
     * @param manifest loaded manifest.
     * @return known feature ids.
     */
    private Set<String> knownIds(FeatureScopeManifest manifest) {
        Set<String> knownIds = new LinkedHashSet<>();
        manifest.include().forEach(entry -> knownIds.add(entry.id()));
        manifest.conceptualNodes().forEach(node -> knownIds.add(node.id()));
        return knownIds;
    }

    /**
     * Applies the mechanical id rename when the workflow references exactly one unknown id and the manifest has
     * exactly one uncovered eligible feature: every id reference list is rewritten, prose stays untouched. Any other
     * combination is ambiguous and handled as orphan flags plus stubs instead.
     *
     * @param workflow workflow document.
     * @param eligibleById eligible features by id.
     * @param knownIds all manifest-declared ids.
     * @return applied renames as {@code old->new} entries.
     */
    private List<String> applySingleRename(ObjectNode workflow, Map<String, EligibleFeature> eligibleById, Set<String> knownIds) {
        List<String> unknownReferences = referencedIds(workflow).stream().filter(id -> !knownIds.contains(id)).toList();
        Set<String> covered = coveredFeatureIds(workflow);
        List<String> uncovered = eligibleById.keySet().stream().filter(id -> !covered.contains(id)).toList();
        if (unknownReferences.size() != 1 || uncovered.size() != 1) {
            return List.of();
        }
        String oldId = unknownReferences.getFirst();
        String newId = uncovered.getFirst();
        rewriteIdReferences(workflow, oldId, newId);
        return List.of(oldId + "->" + newId);
    }

    /**
     * Rewrites one feature id in every id reference list of the document.
     *
     * @param workflow workflow document.
     * @param oldId id to replace.
     * @param newId replacement id.
     */
    private void rewriteIdReferences(ObjectNode workflow, String oldId, String newId) {
        for (ObjectNode template : objectElements(workflow.withArrayProperty("useCaseTemplates"))) {
            rewriteIdList(template.withArrayProperty("selectedFeatureIds"), oldId, newId);
            rewriteIdList(template.withArrayProperty("deselectedFeatureIds"), oldId, newId);
        }
        for (ObjectNode step : objectElements(workflow.withArrayProperty("steps"))) {
            for (ObjectNode decision : objectElements(step.withArrayProperty("decisions"))) {
                for (ObjectNode option : objectElements(decision.withArrayProperty("options"))) {
                    rewriteIdList(option.withArrayProperty("selects"), oldId, newId);
                    rewriteIdList(option.withArrayProperty("deselects"), oldId, newId);
                }
            }
        }
    }

    /**
     * Replaces occurrences of an id inside one string array.
     *
     * @param ids id array node.
     * @param oldId id to replace.
     * @param newId replacement id.
     */
    private void rewriteIdList(ArrayNode ids, String oldId, String newId) {
        for (int index = 0; index < ids.size(); index++) {
            if (oldId.equals(ids.get(index).asString())) {
                ids.set(index, objectMapper.getNodeFactory().stringNode(newId));
            }
        }
    }

    /**
     * Appends a stub option for an uncovered feature into the decision that already covers its model group; when no
     * decision covers the group, a scaffold decision is appended to the last decision-bearing step and reported for
     * placement review. Wiring is filled, prose carries the TODO sentinel, and nothing existing is modified.
     *
     * @param workflow workflow document.
     * @param feature uncovered eligible feature.
     * @param groupByFeatureId manifest group placement per eligible feature id, used to score decision placement.
     * @param addedOptionIds report sink for appended option ids.
     * @param addedDecisionIds report sink for appended scaffold decisions.
     */
    private void appendStubOption(ObjectNode workflow, EligibleFeature feature, Map<String, String> groupByFeatureId, List<String> addedOptionIds,
            List<String> addedDecisionIds) {
        ObjectNode targetDecision = findDecisionCoveringGroup(workflow, feature.group(), groupByFeatureId);
        if (targetDecision == null) {
            targetDecision = appendScaffoldDecision(workflow, feature, addedDecisionIds);
        }
        ObjectNode stub = objectMapper.createObjectNode();
        stub.put("id", "enable-" + feature.id());
        stub.put("label", feature.label());
        stub.put("description", TODO_PROSE + "Describe this option for teachers.");
        stub.withArrayProperty("selects").add(feature.id());
        stub.withArrayProperty("deselects");
        stub.withArrayProperty("warnings");
        stub.withArrayProperty("enabledOutcome").add(TODO_PROSE + "Describe what this option enables.");
        stub.withArrayProperty("recommendedWhen").add(TODO_PROSE + "Describe when this option fits.");
        stub.withArrayProperty("thingsToKnow").add(TODO_PROSE + "Describe notes and caveats.");
        targetDecision.withArrayProperty("options").add(stub);
        addedOptionIds.add("enable-" + feature.id());
    }

    /**
     * Finds the decision whose existing options select the most features of the given group.
     *
     * @param workflow workflow document.
     * @param group model group id of the new feature.
     * @param groupByFeatureId manifest group placement per eligible feature id.
     * @return best matching decision, or null when no decision covers the group.
     */
    private ObjectNode findDecisionCoveringGroup(ObjectNode workflow, String group, Map<String, String> groupByFeatureId) {
        ObjectNode bestDecision = null;
        int bestScore = 0;
        for (ObjectNode step : objectElements(workflow.withArrayProperty("steps"))) {
            for (ObjectNode decision : objectElements(step.withArrayProperty("decisions"))) {
                int score = groupCoverageScore(decision, group, groupByFeatureId);
                if (score > bestScore) {
                    bestScore = score;
                    bestDecision = decision;
                }
            }
        }
        return bestDecision;
    }

    /**
     * Counts how many selected features of a decision belong to the given group according to the manifest placement.
     *
     * @param decision decision node.
     * @param group model group id.
     * @param groupByFeatureId manifest group placement per eligible feature id.
     * @return number of selects in the group.
     */
    private int groupCoverageScore(ObjectNode decision, String group, Map<String, String> groupByFeatureId) {
        if (group == null) {
            return 0;
        }
        int score = 0;
        for (ObjectNode option : objectElements(decision.withArrayProperty("options"))) {
            for (var selected : option.withArrayProperty("selects")) {
                if (group.equals(groupByFeatureId.get(selected.asString()))) {
                    score++;
                }
            }
        }
        return score;
    }

    /**
     * Appends a scaffold decision for a group no existing decision covers, into the last decision-bearing step.
     *
     * @param workflow workflow document.
     * @param feature uncovered feature.
     * @param addedDecisionIds report sink.
     * @return appended decision node.
     */
    private ObjectNode appendScaffoldDecision(ObjectNode workflow, EligibleFeature feature, List<String> addedDecisionIds) {
        ObjectNode lastDecisionStep = null;
        for (ObjectNode step : objectElements(workflow.withArrayProperty("steps"))) {
            if (!step.withArrayProperty("decisions").isEmpty()) {
                lastDecisionStep = step;
            }
        }
        if (lastDecisionStep == null) {
            lastDecisionStep = (ObjectNode) workflow.withArrayProperty("steps").get(0);
        }
        ObjectNode decision = objectMapper.createObjectNode();
        String decisionId = feature.group() != null ? feature.group() + "-scaffold" : feature.id() + "-scaffold";
        decision.put("id", decisionId);
        decision.put("question", TODO_PROSE + "Ask the guided question for this group.");
        decision.put("description", TODO_PROSE + "Describe this decision for teachers.");
        decision.put("selectionMode", "multiple");
        decision.withArrayProperty("options");
        lastDecisionStep.withArrayProperty("decisions").add(decision);
        addedDecisionIds.add(decisionId);
        return decision;
    }

    /**
     * Ensures the review groups contain an entry for a model group, appending a lean entry when missing; title and
     * order stay derivable at serve time.
     *
     * @param workflow workflow document.
     * @param group model group id, or null when the feature hangs directly below the root.
     * @param addedReviewGroupNodeIds report sink.
     */
    private void ensureReviewGroup(ObjectNode workflow, String group, List<String> addedReviewGroupNodeIds) {
        if (group == null) {
            return;
        }
        ArrayNode reviewGroups = workflow.withArrayProperty("finalReviewGroups");
        for (ObjectNode reviewGroup : objectElements(reviewGroups)) {
            if (group.equals(reviewGroup.path("groupNodeId").asString(null))) {
                return;
            }
        }
        if (addedReviewGroupNodeIds.contains(group)) {
            return;
        }
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("groupNodeId", group);
        reviewGroups.add(entry);
        addedReviewGroupNodeIds.add(group);
    }

    /**
     * Collects every feature id selected by at least one option.
     *
     * @param workflow workflow document.
     * @return covered feature ids.
     */
    private Set<String> coveredFeatureIds(ObjectNode workflow) {
        Set<String> covered = new LinkedHashSet<>();
        for (ObjectNode step : objectElements(workflow.withArrayProperty("steps"))) {
            for (ObjectNode decision : objectElements(step.withArrayProperty("decisions"))) {
                for (ObjectNode option : objectElements(decision.withArrayProperty("options"))) {
                    option.withArrayProperty("selects").forEach(selected -> covered.add(selected.asString()));
                }
            }
        }
        return covered;
    }

    /**
     * Collects every feature id the workflow references in selects, deselects, and template lists.
     *
     * @param workflow workflow document.
     * @return referenced feature ids in document order.
     */
    private List<String> referencedIds(ObjectNode workflow) {
        Set<String> referenced = new LinkedHashSet<>();
        for (ObjectNode template : objectElements(workflow.withArrayProperty("useCaseTemplates"))) {
            template.withArrayProperty("selectedFeatureIds").forEach(id -> referenced.add(id.asString()));
            template.withArrayProperty("deselectedFeatureIds").forEach(id -> referenced.add(id.asString()));
        }
        for (ObjectNode step : objectElements(workflow.withArrayProperty("steps"))) {
            for (ObjectNode decision : objectElements(step.withArrayProperty("decisions"))) {
                for (ObjectNode option : objectElements(decision.withArrayProperty("options"))) {
                    option.withArrayProperty("selects").forEach(id -> referenced.add(id.asString()));
                    option.withArrayProperty("deselects").forEach(id -> referenced.add(id.asString()));
                }
            }
        }
        return List.copyOf(referenced);
    }

    /**
     * Iterates the object elements of an array node.
     *
     * @param array array node.
     * @return object elements in order.
     */
    private List<ObjectNode> objectElements(ArrayNode array) {
        List<ObjectNode> elements = new ArrayList<>();
        array.forEach(element -> {
            if (element instanceof ObjectNode objectNode) {
                elements.add(objectNode);
            }
        });
        return elements;
    }

    /**
     * Builds a human-readable label from a feature id.
     *
     * @param id kebab-case feature id.
     * @return title-cased label.
     */
    private String titleCase(String id) {
        String[] parts = id.split("-");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return label.toString();
    }
}
