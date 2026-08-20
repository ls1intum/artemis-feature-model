package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/** Covers hierarchy assembly, kind-based defaults, mapping derivation, evidence merging, and deterministic ordering. */
class GeneratedModelAssemblerTest {

    private static final String ARTEMIS_COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private final GeneratedModelAssembler assembler = new GeneratedModelAssembler(new ObjectMapper());

    @Test
    void assemblesHierarchyInDepthFirstManifestOrder() {
        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT);

        assertThat(result.model().features()).extracting(FeatureNode::id).containsExactly("root", "alpha-group", "alpha", "always-on", "tech-group", "tech-a",
                "tech-b");
        assertThat(result.model().relations()).extracting(relation -> relation.parentId() + "->" + relation.childId()).containsExactly("root->alpha-group",
                "alpha-group->alpha", "alpha-group->always-on", "root->tech-group", "tech-group->tech-a", "tech-group->tech-b");
        assertThat(result.model().model().id()).isEqualTo("artemis-generated-feature-model");
        assertThat(result.model().model().version()).isEqualTo("0.1.0+0123456789ab");
        assertThat(result.model().model().sourceCommitSha()).isEqualTo(ARTEMIS_COMMIT);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void appliesKindAndCategoryDefaults() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();

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
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.artifactMappings()).hasSize(2);
        assertThat(alpha.artifactMappings().getFirst().path()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.artifactMappings().getFirst().valueWhenSelected().asBoolean()).isTrue();
        assertThat(alpha.artifactMappings().getFirst().valueWhenDeselected().asBoolean()).isFalse();
        assertThat(alpha.artifactMappings().get(1).path()).isEqualTo("artemis.alpha.url");
        assertThat(alpha.artifactMappings().get(1).source()).isEqualTo("environment");

        FeatureNode techA = feature(model, "tech-a");
        assertThat(techA.artifactMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo(".env");
            assertThat(mapping.path()).isEqualTo("SPRING_PROFILES_ACTIVE");
            assertThat(mapping.valueWhenSelected().asString()).isEqualTo("tech-a-profile");
        });
    }

    @Test
    void mergesAnchorEvidenceAndSkipsUsageEvidence() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.source().configKey()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.source().serverConditionClass()).isEqualTo("AlphaEnabled");
        assertThat(alpha.source().evidence()).containsExactly("AlphaEnabled.java:12,30", "Constants.java:5", "config/i18n.json");
    }

    @Test
    void carriesDeclaredConstraintsAndGroupTypes() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();

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
    void reportsConstraintEndpointMissingFromEmittedFeatures() {
        List<ResolvedFeatureScope> curatedWithoutTechB = includes().stream().filter(feature -> !feature.id().equals("tech-b")).toList();

        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), curatedWithoutTechB, candidates(), evidence(), ARTEMIS_COMMIT);

        assertThat(result.model().features()).extracting(FeatureNode::id).doesNotContain("tech-b");
        assertThat(result.model().constraints()).extracting(constraint -> constraint.id()).containsExactly("tech-a-excludes-tech-b");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_DANGLING_GENERATED_CONSTRAINT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("tech-a-excludes-tech-b");
            assertThat(item.message()).contains("target 'tech-b'").contains("not emitted");
        });
    }

    @Test
    void semanticConformanceRejectsEveryManifestControlledSurface() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();
        List<FeatureNode> features = new ArrayList<>(assembled.features());
        FeatureNode alpha = feature(assembled, "alpha");
        FeatureNode changedAlpha = new FeatureNode(alpha.id(), alpha.name(), alpha.kind(), alpha.selectable(), alpha.description(), "disabled", alpha.source(),
                "technical", alpha.visibleTo(), alpha.configurableBy(), List.of("wrong-capability"), List.of(), alpha.extraction());
        features.set(features.indexOf(alpha), changedAlpha);
        features.removeIf(feature -> feature.id().equals("tech-b"));
        features.add(new FeatureNode("undeclared", "Undeclared", "feature", true, null, "disabled", null));

        List<FeatureRelation> relations = assembled.relations().stream().map(relation -> {
            if (relation.childId().equals("alpha")) {
                return new FeatureRelation("root", relation.childId(), "mandatory", null, 99);
            }
            if (relation.childId().equals("tech-group")) {
                return new FeatureRelation(relation.parentId(), relation.childId(), relation.relationType(), "or", relation.order());
            }
            return relation;
        }).toList();
        var constraint = assembled.constraints().getFirst();
        var constraints = List.of(new FeatureConstraint(constraint.id(), "requires", constraint.source(), constraint.target(), constraint.expression(),
                constraint.description()));
        ModelMetadata metadata = new ModelMetadata(assembled.model().id(), assembled.model().name(), assembled.model().version(), "failed",
                assembled.model().sourceCommitSha());
        FeatureModel changed = new FeatureModel(metadata, features, relations, constraints);

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidates(), changed,
                ARTEMIS_COMMIT);

        assertThat(findings).allMatch(item -> item.code().equals(ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH));
        assertThat(findings).extracting(ReportItem::subject).contains("model", "alpha", "tech-b", "undeclared", "tech-group",
                "tech-a-excludes-tech-b");
        assertThat(findings).extracting(ReportItem::message).anyMatch(message -> message.contains("category"))
                .anyMatch(message -> message.contains("default state"))
                .anyMatch(message -> message.contains("required capabilities"))
                .anyMatch(message -> message.contains("artifact mappings"))
                .anyMatch(message -> message.contains("hierarchy relation"))
                .anyMatch(message -> message.contains("constraint semantics"));
    }

    @Test
    void semanticConformanceRejectsAManifestControlledNameChange() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();
        FeatureNode alpha = feature(assembled, "alpha");
        FeatureNode changedAlpha = copyWithText(alpha, "Assembler ignored manifest name", alpha.description());

        assertTextMismatch(assembled, alpha, changedAlpha, "name");
    }

    @Test
    void semanticConformanceRejectsAManifestControlledDescriptionChange() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), ARTEMIS_COMMIT).model();
        FeatureNode techA = feature(assembled, "tech-a");
        FeatureNode changedTechA = copyWithText(techA, techA.name(), "Assembler ignored manifest description");

        assertTextMismatch(assembled, techA, changedTechA, "description");
    }

    private FeatureNode copyWithText(FeatureNode feature, String name, String description) {
        return new FeatureNode(feature.id(), name, feature.kind(), feature.selectable(), description, feature.defaultState(), feature.source(),
                feature.category(), feature.visibleTo(), feature.configurableBy(), feature.requiresCapabilities(), feature.artifactMappings(),
                feature.extraction());
    }

    private void assertTextMismatch(FeatureModel assembled, FeatureNode original, FeatureNode changed, String field) {
        List<FeatureNode> features = new ArrayList<>(assembled.features());
        features.set(features.indexOf(original), changed);
        FeatureModel changedModel = new FeatureModel(assembled.model(), features, assembled.relations(), assembled.constraints());

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidates(), changedModel,
                ARTEMIS_COMMIT);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH);
            assertThat(finding.subject()).isEqualTo(original.id());
            assertThat(finding.message()).contains("Generated " + field + " differs from the resolved manifest");
        });
    }

    private FeatureScopeManifest manifest() {
        List<IncludeEntry> declarations = List.of(declaration("module:alpha", "alpha", "alpha-group"),
                declaration("infra:tech-a", "tech-a", "tech-group"), declaration("infra:tech-b", "tech-b", "tech-group"));
        List<ConceptualNode> conceptualNodes = List.of(new ConceptualNode("root", null, "root", null, null, null, null, "Root", null),
                new ConceptualNode("alpha-group", "root", "group", null, null, null, 1, "Alpha Group", null),
                new ConceptualNode("always-on", "alpha-group", "module", "mandatory", null, null, 2, "Always On", null),
                new ConceptualNode("tech-group", "root", "group", null, "technical", "alternative", 2, "Tech Group", null));
        List<ConstraintEntry> constraints = List.of(new ConstraintEntry("tech-a-excludes-tech-b", "excludes", "tech-a", "tech-b", "Exactly one tech."));
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, declarations, List.of(), conceptualNodes, constraints,
                List.of(), List.of());
    }

    private IncludeEntry declaration(String anchor, String id, String group) {
        return new IncludeEntry(anchor, id, group, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null, null, null);
    }

    private List<ResolvedFeatureScope> includes() {
        MappingHint alphaHint = new MappingHint("application-feature-model.yml", "artemis.alpha.url", "environment", null, null, null);
        MappingHint techHint = new MappingHint(".env", "SPRING_PROFILES_ACTIVE", "selection", "tech-a-profile", null, null);
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
                new EvidenceItem("module:alpha", EvidenceItem.KIND_SERVER_CONSTANT, "src/main/java/Constants.java", 5, "MODULE_FEATURE_ALPHA", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_I18N, "config/i18n.json", null, "alpha", null),
                new EvidenceItem("module:alpha", EvidenceItem.KIND_USAGE_FEATURE_TOGGLE, "src/main/java/AlphaResource.java", 44, "alpha", null));
    }

    private FeatureNode feature(FeatureModel model, String id) {
        return model.features().stream().filter(feature -> feature.id().equals(id)).findFirst().orElseThrow();
    }
}
