package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport.DiffEntry;

/**
 * Produces the classified difference report between the generated and the curated feature model. Every difference
 * carries exactly one class with a written explanation: curated prose and hand-picked evidence are
 * {@code intentional-curation}, values the manifest would have to declare are {@code missing-manifest-entry}, scanned
 * facts contradicting curated declarations are {@code artemis-drift}, and anything the extractor cannot express is
 * {@code extractor-gap}. The deliberate E3 additions — the technical subtree and its constraints — are intentional
 * curation by definition.
 */
class ModelDiffService {

    /**
     * Compares the generated model against the curated model.
     *
     * @param curated curated bundled model.
     * @param generated assembled generated model.
     * @param catalogDiff regenerated catalog diff.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @return classified diff report.
     */
    ModelDiffReport compare(FeatureModel curated, FeatureModel generated, ModelDiffReport.CatalogDiff catalogDiff, String artemisCommit) {
        List<DiffEntry> entries = new ArrayList<>();
        Set<String> technicalIds = technicalFeatureIds(generated);
        compareMetadata(curated, generated, entries);
        compareFeatures(curated, generated, entries);
        compareRelations(curated, generated, technicalIds, entries);
        compareConstraints(curated, generated, technicalIds, entries);

        entries.sort(Comparator.comparing(DiffEntry::subject).thenComparing(DiffEntry::aspect).thenComparing(DiffEntry::classification));
        Map<String, Integer> classificationCounts = initializedCounts();
        entries.forEach(entry -> classificationCounts.merge(entry.classification(), 1, Integer::sum));
        return new ModelDiffReport(generated.model().id(), generated.model().version(), curated.model().id(), curated.model().version(), artemisCommit,
                classificationCounts, entries, catalogDiff);
    }

    /**
     * Collects the ids of technical features and groups of the generated model.
     *
     * @param generated generated model.
     * @return technical feature ids.
     */
    private Set<String> technicalFeatureIds(FeatureModel generated) {
        Set<String> technicalIds = new LinkedHashSet<>();
        for (FeatureNode feature : generated.features()) {
            if (FeatureScopeManifest.CATEGORY_TECHNICAL.equals(feature.category())) {
                technicalIds.add(feature.id());
            }
        }
        return technicalIds;
    }

    /**
     * Compares the model metadata; the generated model is a distinct artifact by design.
     *
     * @param curated curated model.
     * @param generated generated model.
     * @param entries entry sink.
     */
    private void compareMetadata(FeatureModel curated, FeatureModel generated, List<DiffEntry> entries) {
        if (!Objects.equals(curated.model().id(), generated.model().id())) {
            entries.add(new DiffEntry(ModelDiffReport.CLASS_INTENTIONAL_CURATION, "model-metadata", "model.id", curated.model().id(), generated.model().id(),
                    "The generated model is a parallel snapshot artifact with its own identity."));
        }
        if (!Objects.equals(curated.model().version(), generated.model().version())) {
            entries.add(new DiffEntry(ModelDiffReport.CLASS_INTENTIONAL_CURATION, "model-metadata", "model.version", curated.model().version(),
                    generated.model().version(), "The generated model version encodes the scanned Artemis commit."));
        }
    }

    /**
     * Compares the feature sets and, on the intersection, every feature aspect.
     *
     * @param curated curated model.
     * @param generated generated model.
     * @param entries entry sink.
     */
    private void compareFeatures(FeatureModel curated, FeatureModel generated, List<DiffEntry> entries) {
        Map<String, FeatureNode> curatedById = featuresById(curated);
        Map<String, FeatureNode> generatedById = featuresById(generated);
        for (FeatureNode curatedFeature : curated.features()) {
            FeatureNode generatedFeature = generatedById.get(curatedFeature.id());
            if (generatedFeature == null) {
                entries.add(new DiffEntry(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-membership", curatedFeature.id(), "present", null,
                        "The manifest has no include or conceptual entry producing this curated feature."));
                continue;
            }
            compareFeatureAspects(curatedFeature, generatedFeature, entries);
        }
        for (FeatureNode generatedFeature : generated.features()) {
            if (!curatedById.containsKey(generatedFeature.id())) {
                String explanation = FeatureScopeManifest.CATEGORY_TECHNICAL.equals(generatedFeature.category())
                        ? "E3 deliberately introduces the technical subtree beyond the functional-only curated model."
                        : "The manifest deliberately includes this feature beyond the curated scope.";
                entries.add(new DiffEntry(ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-membership", generatedFeature.id(), null, "present", explanation));
            }
        }
    }

    /**
     * Compares every aspect of one feature present in both models.
     *
     * @param curated curated feature.
     * @param generated generated feature.
     * @param entries entry sink.
     */
    private void compareFeatureAspects(FeatureNode curated, FeatureNode generated, List<DiffEntry> entries) {
        addIfDifferent(entries, ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-name", curated.id(), curated.name(), generated.name(),
                "Curated names are hand-written; generated names come from Artemis i18n or the manifest.");
        addIfDifferent(entries, ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-description", curated.id(), curated.description(), generated.description(),
                "Curated descriptions are hand-written; generated descriptions come from Artemis i18n or javadoc.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-kind", curated.id(), curated.kind(), generated.kind(),
                "The feature kind is manifest data.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-selectable", curated.id(), String.valueOf(curated.selectable()),
                String.valueOf(generated.selectable()), "Selectability follows the manifest-declared kind.");
        addIfDifferent(entries, ModelDiffReport.CLASS_ARTEMIS_DRIFT, "feature-default-state", curated.id(), curated.defaultState(), generated.defaultState(),
                "The generated default state restates the scanned Artemis YAML default.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-category", curated.id(), curated.category(), generated.category(),
                "The category is manifest data with kind-based defaults.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-visible-to", curated.id(), String.valueOf(curated.visibleTo()),
                String.valueOf(generated.visibleTo()), "Role visibility follows the manifest-declared category.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-configurable-by", curated.id(), String.valueOf(curated.configurableBy()),
                String.valueOf(generated.configurableBy()), "Configurability follows the manifest-declared category.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-requires-capabilities", curated.id(),
                String.valueOf(curated.requiresCapabilities()), String.valueOf(generated.requiresCapabilities()),
                "Capability requirements are manifest or annotation data.");
        addIfDifferent(entries, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "feature-artifact-mappings", curated.id(), renderMappings(curated.artifactMappings()),
                renderMappings(generated.artifactMappings()), "Mappings are auto-derived from the enabled key plus declared manifest hints.");
        compareSourceAspects(curated, generated, entries);
        addIfDifferent(entries, ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-extraction-block", curated.id(), renderExtraction(curated),
                renderExtraction(generated), "Curated features are manually confirmed; generated features carry the automatic extraction block.");
    }

    /**
     * Compares the source anchors and evidence of one feature.
     *
     * @param curated curated feature.
     * @param generated generated feature.
     * @param entries entry sink.
     */
    private void compareSourceAspects(FeatureNode curated, FeatureNode generated, List<DiffEntry> entries) {
        FeatureSource curatedSource = curated.source();
        FeatureSource generatedSource = generated.source();
        if (curatedSource == null && generatedSource == null) {
            return;
        }
        if (curatedSource == null || generatedSource == null) {
            String explanation = generatedSource == null
                    ? "The generated conceptual node claims no source anchor; the curated evidence is hand-picked."
                    : "The scan anchors this feature while the curated model records no source block.";
            entries.add(new DiffEntry(ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-source", curated.id(), renderSource(curatedSource),
                    renderSource(generatedSource), explanation));
            return;
        }
        addIfDifferent(entries, ModelDiffReport.CLASS_ARTEMIS_DRIFT, "feature-source-config-key", curated.id(), curatedSource.configKey(),
                generatedSource.configKey(), "The scanned configuration key disagrees with the curated declaration.");
        addIfDifferent(entries, ModelDiffReport.CLASS_ARTEMIS_DRIFT, "feature-source-condition-class", curated.id(), curatedSource.serverConditionClass(),
                generatedSource.serverConditionClass(), "The scanned condition class disagrees with the curated declaration.");
        addIfDifferent(entries, ModelDiffReport.CLASS_ARTEMIS_DRIFT, "feature-source-client-constant", curated.id(), curatedSource.clientConstant(),
                generatedSource.clientConstant(), "The scanned client constant disagrees with the curated declaration.");
        addIfDifferent(entries, ModelDiffReport.CLASS_ARTEMIS_DRIFT, "feature-source-spring-profile", curated.id(), curatedSource.springProfile(),
                generatedSource.springProfile(), "The scanned Spring profile disagrees with the curated declaration.");
        addIfDifferent(entries, ModelDiffReport.CLASS_INTENTIONAL_CURATION, "feature-source-evidence", curated.id(), String.valueOf(curatedSource.evidence()),
                String.valueOf(generatedSource.evidence()), "Curated evidence references are hand-picked; generated references merge the scan evidence.");
    }

    /**
     * Compares the relation sets keyed by parent and child.
     *
     * @param curated curated model.
     * @param generated generated model.
     * @param technicalIds technical feature ids of the generated model.
     * @param entries entry sink.
     */
    private void compareRelations(FeatureModel curated, FeatureModel generated, Set<String> technicalIds, List<DiffEntry> entries) {
        Map<String, FeatureRelation> curatedRelations = relationsByKey(curated);
        Map<String, FeatureRelation> generatedRelations = relationsByKey(generated);
        for (Map.Entry<String, FeatureRelation> entry : curatedRelations.entrySet()) {
            FeatureRelation generatedRelation = generatedRelations.get(entry.getKey());
            if (generatedRelation == null) {
                entries.add(new DiffEntry(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "relation", entry.getKey(), renderRelation(entry.getValue()), null,
                        "The manifest hierarchy does not reproduce this curated relation."));
            }
            else if (!entry.getValue().equals(generatedRelation)) {
                entries.add(new DiffEntry(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "relation", entry.getKey(), renderRelation(entry.getValue()),
                        renderRelation(generatedRelation), "Relation type, group type, and order are manifest data."));
            }
        }
        for (Map.Entry<String, FeatureRelation> entry : generatedRelations.entrySet()) {
            if (curatedRelations.containsKey(entry.getKey())) {
                continue;
            }
            FeatureRelation relation = entry.getValue();
            boolean technical = technicalIds.contains(relation.parentId()) || technicalIds.contains(relation.childId());
            String classification = technical ? ModelDiffReport.CLASS_INTENTIONAL_CURATION : ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY;
            String explanation = technical ? "E3 deliberately introduces the technical subtree relations."
                    : "The manifest declares a relation the curated model does not contain.";
            entries.add(new DiffEntry(classification, "relation", entry.getKey(), null, renderRelation(relation), explanation));
        }
    }

    /**
     * Compares the constraint sets by id.
     *
     * @param curated curated model.
     * @param generated generated model.
     * @param technicalIds technical feature ids of the generated model.
     * @param entries entry sink.
     */
    private void compareConstraints(FeatureModel curated, FeatureModel generated, Set<String> technicalIds, List<DiffEntry> entries) {
        Map<String, FeatureConstraint> curatedConstraints = constraintsById(curated);
        Map<String, FeatureConstraint> generatedConstraints = constraintsById(generated);
        for (Map.Entry<String, FeatureConstraint> entry : curatedConstraints.entrySet()) {
            FeatureConstraint generatedConstraint = generatedConstraints.get(entry.getKey());
            if (generatedConstraint == null) {
                entries.add(new DiffEntry(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "constraint", entry.getKey(), renderConstraint(entry.getValue()), null,
                        "The manifest declares no constraint reproducing this curated constraint."));
            }
            else if (!renderConstraint(entry.getValue()).equals(renderConstraint(generatedConstraint))) {
                entries.add(new DiffEntry(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, "constraint", entry.getKey(), renderConstraint(entry.getValue()),
                        renderConstraint(generatedConstraint), "Constraint fields are manifest data."));
            }
        }
        for (Map.Entry<String, FeatureConstraint> entry : generatedConstraints.entrySet()) {
            if (curatedConstraints.containsKey(entry.getKey())) {
                continue;
            }
            FeatureConstraint constraint = entry.getValue();
            boolean technical = technicalIds.contains(constraint.source()) || technicalIds.contains(constraint.target());
            String classification = technical ? ModelDiffReport.CLASS_INTENTIONAL_CURATION : ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY;
            String explanation = technical ? "E3 deliberately introduces the technical xor exclusivity constraints."
                    : "The manifest declares a constraint the curated model does not contain.";
            entries.add(new DiffEntry(classification, "constraint", entry.getKey(), null, renderConstraint(constraint), explanation));
        }
    }

    /**
     * Adds a diff entry when the two renderings differ.
     *
     * @param entries entry sink.
     * @param classification difference class.
     * @param aspect compared aspect.
     * @param subject subject id.
     * @param curated curated value rendering, or null.
     * @param generated generated value rendering, or null.
     * @param explanation classification explanation.
     */
    private void addIfDifferent(List<DiffEntry> entries, String classification, String aspect, String subject, String curated, String generated,
            String explanation) {
        if (!Objects.equals(curated, generated)) {
            entries.add(new DiffEntry(classification, aspect, subject, curated, generated, explanation));
        }
    }

    /**
     * Indexes features by id, keeping the first declaration on duplicates.
     *
     * @param model model to index.
     * @return features keyed by id.
     */
    private Map<String, FeatureNode> featuresById(FeatureModel model) {
        Map<String, FeatureNode> featuresById = new LinkedHashMap<>();
        model.features().forEach(feature -> featuresById.putIfAbsent(feature.id(), feature));
        return featuresById;
    }

    /**
     * Indexes relations by parent and child key.
     *
     * @param model model to index.
     * @return relations keyed by {@code parent->child}.
     */
    private Map<String, FeatureRelation> relationsByKey(FeatureModel model) {
        Map<String, FeatureRelation> relationsByKey = new LinkedHashMap<>();
        model.relations().forEach(relation -> relationsByKey.putIfAbsent(relation.parentId() + "->" + relation.childId(), relation));
        return relationsByKey;
    }

    /**
     * Indexes constraints by id.
     *
     * @param model model to index.
     * @return constraints keyed by id.
     */
    private Map<String, FeatureConstraint> constraintsById(FeatureModel model) {
        Map<String, FeatureConstraint> constraintsById = new LinkedHashMap<>();
        model.constraints().forEach(constraint -> constraintsById.putIfAbsent(constraint.id(), constraint));
        return constraintsById;
    }

    /**
     * Renders a source block for diff output.
     *
     * @param source source block, or null.
     * @return compact rendering, or null.
     */
    private String renderSource(FeatureSource source) {
        if (source == null) {
            return null;
        }
        return "configKey=" + source.configKey() + ", conditionClass=" + source.serverConditionClass() + ", evidence=" + source.evidence().size() + " item(s)";
    }

    /**
     * Renders an artifact mapping list for diff output.
     *
     * @param mappings artifact mappings.
     * @return compact rendering.
     */
    private String renderMappings(List<ArtifactMapping> mappings) {
        List<String> rendered = new ArrayList<>();
        for (ArtifactMapping mapping : mappings) {
            rendered.add(mapping.target() + ":" + mapping.path() + (mapping.isProfileValue() ? "<-" + mapping.valueFromProfile() : "")
                    + (Boolean.TRUE.equals(mapping.requiredWhenSelected()) ? "!required" : "") + (Boolean.TRUE.equals(mapping.secret()) ? "!secret" : ""));
        }
        return String.valueOf(rendered);
    }

    /**
     * Renders an extraction block for diff output.
     *
     * @param feature feature carrying the block.
     * @return compact rendering.
     */
    private String renderExtraction(FeatureNode feature) {
        if (feature.extraction() == null) {
            return null;
        }
        return feature.extraction().method() + "/" + feature.extraction().confidence() + "/" + feature.extraction().status();
    }

    /**
     * Renders a relation for diff output.
     *
     * @param relation relation to render.
     * @return compact rendering.
     */
    private String renderRelation(FeatureRelation relation) {
        return relation.relationType() + (relation.groupType() == null ? "" : "(" + relation.groupType() + ")") + " order=" + relation.order();
    }

    /**
     * Renders a constraint for diff output.
     *
     * @param constraint constraint to render.
     * @return compact rendering.
     */
    private String renderConstraint(FeatureConstraint constraint) {
        return constraint.type() + " " + constraint.source() + " -> " + constraint.target();
    }

    /**
     * Creates a classification count map with all four classes present, so report consumers see explicit zeros.
     *
     * @return mutable count map initialized to zero.
     */
    private Map<String, Integer> initializedCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        counts.put(ModelDiffReport.CLASS_INTENTIONAL_CURATION, 0);
        counts.put(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, 0);
        counts.put(ModelDiffReport.CLASS_ARTEMIS_DRIFT, 0);
        counts.put(ModelDiffReport.CLASS_EXTRACTOR_GAP, 0);
        return counts;
    }
}
