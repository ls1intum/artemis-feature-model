package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
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

/** Covers hierarchy assembly, kind-based defaults, mapping derivation, evidence merging, and deterministic ordering. */
class GeneratedModelAssemblerTest {

    private final GeneratedModelAssembler assembler = new GeneratedModelAssembler(new ObjectMapper());

    @Test
    void assemblesHierarchyInDepthFirstManifestOrder() {
        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(), "0123456789abcdef");

        assertThat(result.model().features()).extracting(FeatureNode::id).containsExactly("root", "alpha-group", "alpha", "always-on", "tech-group", "tech-a",
                "tech-b");
        assertThat(result.model().relations()).extracting(relation -> relation.parentId() + "->" + relation.childId()).containsExactly("root->alpha-group",
                "alpha-group->alpha", "alpha-group->always-on", "root->tech-group", "tech-group->tech-a", "tech-group->tech-b");
        assertThat(result.model().model().id()).isEqualTo("artemis-generated-feature-model");
        assertThat(result.model().model().version()).isEqualTo("0.1.0+0123456789ab");
        assertThat(result.model().model().sourceCommitSha()).isEqualTo("0123456789abcdef");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void appliesKindAndCategoryDefaults() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(), "0123456789abcdef").model();

        FeatureNode root = feature(model, "root");
        assertThat(root.selectable()).isFalse();
        assertThat(root.category()).isEqualTo("derived");
        assertThat(root.defaultState()).isEqualTo("not_applicable");

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.selectable()).isTrue();
        assertThat(alpha.category()).isEqualTo("functional");
        assertThat(alpha.visibleTo()).containsExactly("teacher", "maintainer");
        assertThat(alpha.configurableBy()).containsExactly("teacher", "maintainer");
        assertThat(alpha.defaultState()).isEqualTo("enabled");
        assertThat(alpha.name()).isEqualTo("Alpha From I18n");
        assertThat(alpha.extraction().method()).isEqualTo("automatic");
        assertThat(alpha.extraction().confidence()).isEqualTo("high");

        FeatureNode alwaysOn = feature(model, "always-on");
        assertThat(alwaysOn.selectable()).isTrue();
        assertThat(alwaysOn.defaultState()).isEqualTo("enabled");
        assertThat(alwaysOn.extraction().method()).isEqualTo("manual-curation");

        FeatureNode techA = feature(model, "tech-a");
        assertThat(techA.category()).isEqualTo("technical");
        assertThat(techA.visibleTo()).containsExactly("maintainer");
        assertThat(techA.configurableBy()).containsExactly("maintainer");
        assertThat(techA.defaultState()).isEqualTo("enabled");
        assertThat(techA.name()).isEqualTo("Tech A");
    }

    @Test
    void derivesEnabledKeyMappingAndAppendsDeclaredHints() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(), "0123456789abcdef").model();

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.artifactMappings()).hasSize(2);
        assertThat(alpha.artifactMappings().getFirst().path()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.artifactMappings().getFirst().valueWhenSelected().asBoolean()).isTrue();
        assertThat(alpha.artifactMappings().getFirst().valueWhenDeselected().asBoolean()).isFalse();
        assertThat(alpha.artifactMappings().get(1).path()).isEqualTo("artemis.alpha.url");
        assertThat(alpha.artifactMappings().get(1).valueFromProfile()).isEqualTo("artemis.alpha.url");
        assertThat(alpha.artifactMappings().get(1).requiredWhenSelected()).isTrue();

        FeatureNode techA = feature(model, "tech-a");
        assertThat(techA.artifactMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo(".env");
            assertThat(mapping.path()).isEqualTo("SPRING_PROFILES_ACTIVE");
            assertThat(mapping.valueWhenSelected().asString()).isEqualTo("tech-a-profile");
        });
    }

    @Test
    void mergesAnchorEvidenceAndSkipsUsageEvidence() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(), "0123456789abcdef").model();

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.source().configKey()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.source().backendConditionClass()).isEqualTo("AlphaEnabled");
        assertThat(alpha.source().evidence()).containsExactly("AlphaEnabled.java:12,30", "Constants.java:5", "config/i18n.json");
    }

    @Test
    void carriesDeclaredConstraintsAndGroupTypes() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(), "0123456789abcdef").model();

        FeatureRelation techGroupRelation = model.relations().stream().filter(relation -> relation.childId().equals("tech-group")).findFirst().orElseThrow();
        assertThat(techGroupRelation.relationType()).isEqualTo("group");
        assertThat(techGroupRelation.groupType()).isEqualTo("alternative");
        FeatureRelation alwaysOnRelation = model.relations().stream().filter(relation -> relation.childId().equals("always-on")).findFirst().orElseThrow();
        assertThat(alwaysOnRelation.relationType()).isEqualTo("mandatory");

        assertThat(model.constraints()).singleElement().satisfies(constraint -> {
            assertThat(constraint.id()).isEqualTo("tech-a-excludes-tech-b");
            assertThat(constraint.type()).isEqualTo("excludes");
        });
    }

    @Test
    void reportsDirectedRelationCandidateWithoutDeclaredConstraint() {
        RelationCandidate undeclared = new RelationCandidate("relation:AlphaWithTechA", "requires", "module:alpha", List.of("module:alpha", "infra:tech-a"),
                true, "AlphaWithTechAEnabled", "candidate", "alpha AND tech-a");

        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), includes(), candidates(), evidence(), List.of(undeclared), "0123456789abcdef");

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.message()).contains("alpha").contains("tech-a");
        });
    }

    private FeatureScopeManifest manifest() {
        List<ConceptualNode> conceptualNodes = List.of(new ConceptualNode("root", null, "root", null, null, null, null, "Root", null),
                new ConceptualNode("alpha-group", "root", "group", null, null, null, 1, "Alpha Group", null),
                new ConceptualNode("always-on", "alpha-group", "module", "mandatory", null, null, 2, "Always On", null),
                new ConceptualNode("tech-group", "root", "group", null, "technical", "alternative", 2, "Tech Group", null));
        List<ConstraintEntry> constraints = List.of(new ConstraintEntry("tech-a-excludes-tech-b", "excludes", "tech-a", "tech-b", "Exactly one tech."));
        return new FeatureScopeManifest(1, "0123456789abcdef", List.of(), List.of(), conceptualNodes, constraints, List.of());
    }

    private List<ResolvedFeatureScope> includes() {
        MappingHint alphaHint = new MappingHint("application-feature-model.yml", "artemis.alpha.url", null, null, "artemis.alpha.url", true, null);
        MappingHint techHint = new MappingHint(".env", "SPRING_PROFILES_ACTIVE", "tech-a-profile", null, null, null, null);
        return List.of(
                new ResolvedFeatureScope("module:alpha", "alpha", "alpha-group", null, "module", "optional", null, null, 1, List.of("alpha-service"), List.of(),
                        List.of(alphaHint), null, null, null, "manifest"),
                new ResolvedFeatureScope("infra:tech-a", "tech-a", "tech-group", null, "feature", "optional", "technical", "enabled", 1, List.of(),
                        List.of("tech-capability"), List.of(techHint), "Tech A", "Technical alternative A.", null, "manifest"),
                new ResolvedFeatureScope("infra:tech-b", "tech-b", "tech-group", null, "feature", "optional", "technical", "disabled", 2, List.of(), List.of(),
                        List.of(), "Tech B", "Technical alternative B.", null, "manifest"));
    }

    private List<FeatureCandidate> candidates() {
        return List.of(new FeatureCandidate("module:alpha", FeatureCandidate.KIND_MODULE_FEATURE, "Alpha From I18n", "Alpha description.", null,
                "artemis.alpha.enabled", Boolean.TRUE, "ALPHA_ENABLED_PROPERTY_NAME", "MODULE_FEATURE_ALPHA", "AlphaEnabled", null, true, true, null),
                new FeatureCandidate("infra:tech-a", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null, null, null, null),
                new FeatureCandidate("infra:tech-b", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    private List<EvidenceItem> evidence() {
        return List.of(new EvidenceItem("module:alpha", EvidenceItem.KIND_CONDITION_CLASS, "src/main/java/AlphaEnabled.java", 12, "AlphaEnabled", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_CONDITION_CLASS, "src/main/java/AlphaEnabled.java", 30, "AlphaEnabled", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_BACKEND_CONSTANT, "src/main/java/Constants.java", 5, "MODULE_FEATURE_ALPHA", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_I18N, "config/i18n.json", null, "alpha", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_USAGE_FEATURE_TOGGLE, "src/main/java/AlphaResource.java", 44, "alpha", null));
    }

    private FeatureNode feature(FeatureModel model, String id) {
        return model.features().stream().filter(feature -> feature.id().equals(id)).findFirst().orElseThrow();
    }
}
