package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.FeatureEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/** Covers hierarchy assembly, kind-based defaults, mapping layout, evidence merging, derived constraints, the implicit root, sub-feature nodes, and deterministic ordering. */
class GeneratedModelAssemblerTest {

    private static final String ARTEMIS_COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private final GeneratedModelAssembler assembler = new GeneratedModelAssembler(new ObjectMapper());

    @Test
    void assemblesHierarchyInDepthFirstManifestOrder() {
        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT);

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
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();

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
    void derivesEnabledKeyMappingAndAppendsResolvedMappings() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();

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
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();

        FeatureNode alpha = feature(model, "alpha");
        assertThat(alpha.source().configKey()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.source().serverConditionClass()).isEqualTo("AlphaEnabled");
        assertThat(alpha.source().evidence()).containsExactly("AlphaEnabled.java:12,30", "Constants.java:5", "config/i18n.json");
    }

    @Test
    void carriesDeclaredAndDerivedConstraintsAndGroupTypes() {
        FeatureModel model = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();

        FeatureRelation techGroupRelation = model.relations().stream().filter(relation -> relation.childId().equals("tech-group")).findFirst().orElseThrow();
        assertThat(techGroupRelation.relationType()).isEqualTo("group");
        assertThat(techGroupRelation.groupType()).isEqualTo("alternative");
        FeatureRelation alwaysOnRelation = model.relations().stream().filter(relation -> relation.childId().equals("always-on")).findFirst().orElseThrow();
        assertThat(alwaysOnRelation.relationType()).isEqualTo("mandatory");

        assertThat(model.constraints()).extracting(FeatureConstraint::id).as("declared constraints first, then the derived alternative-group exclusions")
                .containsExactly("alpha-requires-tech-a", "tech-a-excludes-tech-b");
        assertThat(model.constraints().getLast()).satisfies(constraint -> {
            assertThat(constraint.type()).isEqualTo("excludes");
            assertThat(constraint.source()).isEqualTo("tech-a");
            assertThat(constraint.target()).isEqualTo("tech-b");
            assertThat(constraint.description()).isEqualTo("A deployment selects exactly one option of Tech Group; Tech A and Tech B are mutually exclusive.");
        });
    }

    @Test
    void dropsADeclaredConstraintThatDuplicatesADerivedExclusionWithAWarning() {
        ConstraintEntry redundant = new ConstraintEntry("tech-b-excludes-tech-a", "excludes", "tech-b", "tech-a", "Declared twice.");
        FeatureScopeManifest manifest = manifest(List.of(redundant));

        GeneratedModelAssembler.Result result = assembler.assemble(manifest, includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT);

        assertThat(result.model().constraints()).extracting(FeatureConstraint::id).containsExactly("tech-a-excludes-tech-b");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CONSTRAINT_REDUNDANT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.subject()).isEqualTo("tech-b-excludes-tech-a");
        });
        assertThat(new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest, includes(), candidates(), List.of(), result.model(), ARTEMIS_COMMIT)).isEmpty();
    }

    @Test
    void emitsTheImplicitRootWhenTheManifestDeclaresNone() {
        List<ConceptualNode> withoutRoot = List.of(new ConceptualNode("alpha-group", FeatureScopeManifest.IMPLICIT_ROOT_ID, "group", null, null, null, 1, "Alpha Group", null),
                new ConceptualNode("tech-group", FeatureScopeManifest.IMPLICIT_ROOT_ID, "group", null, "technical", "alternative", 2, "Tech Group", null));
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(), List.of(), List.of(), withoutRoot, List.of(),
                List.of());

        GeneratedModelAssembler.Result result = assembler.assemble(manifest, includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT);

        assertThat(result.items()).isEmpty();
        assertThat(result.model().features()).extracting(FeatureNode::id).containsExactly("artemis", "alpha-group", "alpha", "tech-group", "tech-a", "tech-b");
        FeatureNode root = feature(result.model(), "artemis");
        assertThat(root.name()).isEqualTo("Artemis");
        assertThat(root.description()).isEqualTo("Root of the Artemis feature model.");
        assertThat(root.kind()).isEqualTo("root");
        assertThat(root.selectable()).isFalse();
        assertThat(result.model().relations().getFirst().parentId()).isEqualTo("artemis");
        assertThat(new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest, includes(), candidates(), List.of(), result.model(), ARTEMIS_COMMIT)).isEmpty();
    }


    @Test
    void reportsConstraintEndpointMissingFromEmittedFeatures() {
        List<ResolvedFeatureScope> curatedWithoutTechA = includes().stream().filter(feature -> !feature.id().equals("tech-a")).toList();

        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), curatedWithoutTechA, candidates(), evidence(), Map.of(), ARTEMIS_COMMIT);

        assertThat(result.model().features()).extracting(FeatureNode::id).doesNotContain("tech-a");
        assertThat(result.model().constraints()).extracting(constraint -> constraint.id()).as("a single remaining alternative derives no exclusion")
                .containsExactly("alpha-requires-tech-a");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_DANGLING_GENERATED_CONSTRAINT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("alpha-requires-tech-a");
            assertThat(item.message()).contains("target 'tech-a'").contains("not emitted");
        });
    }

    @Test
    void semanticConformanceRejectsEveryManifestControlledSurface() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();
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
        var constraints = List.of(new FeatureConstraint(constraint.id(), "excludes", constraint.source(), constraint.target(), constraint.expression(),
                constraint.description()), assembled.constraints().getLast());
        ModelMetadata metadata = new ModelMetadata(assembled.model().id(), assembled.model().name(), assembled.model().version(), "failed",
                assembled.model().sourceCommitSha());
        FeatureModel changed = new FeatureModel(metadata, features, relations, constraints);

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidates(), List.of(), changed,
                ARTEMIS_COMMIT);

        assertThat(findings).allMatch(item -> item.code().equals(ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH));
        assertThat(findings).extracting(ReportItem::subject).contains("model", "alpha", "tech-b", "undeclared", "tech-group",
                "alpha-requires-tech-a");
        assertThat(findings).extracting(ReportItem::message).anyMatch(message -> message.contains("category"))
                .anyMatch(message -> message.contains("default state"))
                .anyMatch(message -> message.contains("required capabilities"))
                .anyMatch(message -> message.contains("artifact mappings"))
                .anyMatch(message -> message.contains("hierarchy relation"))
                .anyMatch(message -> message.contains("constraint semantics"));
    }

    @Test
    void semanticConformanceRejectsAManifestControlledNameChange() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();
        FeatureNode alpha = feature(assembled, "alpha");
        FeatureNode changedAlpha = copyWithText(alpha, "Assembler ignored manifest name", alpha.description());

        assertTextMismatch(assembled, alpha, changedAlpha, "name");
    }

    @Test
    void semanticConformanceRejectsAManifestControlledDescriptionChange() {
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidates(), evidence(), Map.of(), ARTEMIS_COMMIT).model();
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

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidates(), List.of(), changedModel,
                ARTEMIS_COMMIT);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH);
            assertThat(finding.subject()).isEqualTo(original.id());
            assertThat(finding.message()).contains("Generated " + field + " differs from the resolved manifest");
        });
    }

    @Test
    void emitsSubFeatureNodesBelowTheirOwnersPerContract() {
        FeatureUsageJoin.Result join = FeatureUsageJoin.join(includes(), candidatesWithTechProfile(), usages());

        GeneratedModelAssembler.Result result = assembler.assemble(manifest(), includes(), candidatesWithTechProfile(), evidence(), join.subFeaturesByOwner(),
                ARTEMIS_COMMIT);

        FeatureModel model = result.model();
        assertThat(result.items()).isEmpty();
        assertThat(model.features()).extracting(FeatureNode::id).containsExactly("root", "alpha-group", "alpha", "alpha/authoring/items", "alpha/chat/chat-sessions",
                "always-on", "tech-group", "tech-a", "tech-a/build/agents", "tech-b");
        assertThat(model.relations()).filteredOn(relation -> relation.parentId().equals("alpha"))
                .extracting(FeatureRelation::childId, FeatureRelation::relationType, FeatureRelation::groupType, FeatureRelation::order)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("alpha/authoring/items", "mandatory", null, 1),
                        org.assertj.core.groups.Tuple.tuple("alpha/chat/chat-sessions", "mandatory", null, 2));

        FeatureNode chat = feature(model, "alpha/chat/chat-sessions");
        assertThat(chat.name()).isEqualTo("Chat sessions");
        assertThat(chat.kind()).isEqualTo("sub-feature");
        assertThat(chat.selectable()).isFalse();
        assertThat(chat.description()).isEqualTo("REST endpoints labelled chat/chat-sessions in module alpha, guarded by AlphaEnabled.");
        assertThat(chat.defaultState()).isEqualTo("not_applicable");
        assertThat(chat.category()).isEqualTo("derived");
        assertThat(chat.visibleTo()).containsExactly("teacher", "maintainer");
        assertThat(chat.configurableBy()).isEmpty();
        assertThat(chat.requiresCapabilities()).isEmpty();
        assertThat(chat.artifactMappings()).isEmpty();
        assertThat(chat.source().usageLabel()).isEqualTo("chat/chat-sessions");
        assertThat(chat.source().serverConditionClass()).isEqualTo("AlphaEnabled");
        assertThat(chat.source().springProfile()).isNull();
        assertThat(chat.source().evidence()).containsExactly("AlphaChatResource.java:57");
        assertThat(chat.extraction().method()).isEqualTo("feature-usage-annotation");

        FeatureNode agents = feature(model, "tech-a/build/agents");
        assertThat(agents.visibleTo()).as("sub-features of technical owners are maintainer-only").containsExactly("maintainer");
        assertThat(agents.source().springProfile()).isEqualTo("tech-a");
        assertThat(agents.source().serverConditionClass()).isNull();
        assertThat(agents.description()).isEqualTo("REST endpoints labelled build/agents in module tech, guarded by PROFILE_TECH_A.");

        assertThat(new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidatesWithTechProfile(), usages(), model,
                ARTEMIS_COMMIT)).as("the conformance step recomputes the same sub-features from the usages").isEmpty();
    }

    @Test
    void conformanceRejectsMissingAndExtraSubFeatureNodes() {
        FeatureUsageJoin.Result join = FeatureUsageJoin.join(includes(), candidatesWithTechProfile(), usages());
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidatesWithTechProfile(), evidence(), join.subFeaturesByOwner(), ARTEMIS_COMMIT)
                .model();
        List<FeatureNode> features = new ArrayList<>(assembled.features());
        FeatureNode chat = feature(assembled, "alpha/chat/chat-sessions");
        features.remove(chat);
        features.add(new FeatureNode("alpha/chat/extra", "Extra", "sub-feature", false, null, "not_applicable", chat.source(), "derived", chat.visibleTo(),
                List.of(), List.of(), List.of(), chat.extraction()));
        FeatureModel changed = new FeatureModel(assembled.model(), features, assembled.relations(), assembled.constraints());

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidatesWithTechProfile(), usages(),
                changed, ARTEMIS_COMMIT);

        assertThat(findings).extracting(ReportItem::subject).contains("alpha/chat/chat-sessions", "alpha/chat/extra");
        assertThat(findings).extracting(ReportItem::message).anyMatch(message -> message.contains("missing sub-feature"))
                .anyMatch(message -> message.contains("undeclared feature 'alpha/chat/extra'"));
    }

    @Test
    void conformanceRejectsASubFeatureWithTheWrongVisibilityOrEvidence() {
        FeatureUsageJoin.Result join = FeatureUsageJoin.join(includes(), candidatesWithTechProfile(), usages());
        FeatureModel assembled = assembler.assemble(manifest(), includes(), candidatesWithTechProfile(), evidence(), join.subFeaturesByOwner(), ARTEMIS_COMMIT)
                .model();
        List<FeatureNode> features = new ArrayList<>(assembled.features());
        FeatureNode agents = feature(assembled, "tech-a/build/agents");
        features.set(features.indexOf(agents), new FeatureNode(agents.id(), agents.name(), agents.kind(), agents.selectable(), agents.description(),
                agents.defaultState(), new FeatureSource(null, "tech-a", null, null, "build/agents", List.of("Other.java:1")), agents.category(),
                List.of("teacher", "maintainer"), List.of(), List.of(), List.of(), agents.extraction()));
        FeatureModel changed = new FeatureModel(assembled.model(), features, assembled.relations(), assembled.constraints());

        List<ReportItem> findings = new GeneratedModelConformanceService(new ObjectMapper()).validate(manifest(), includes(), candidatesWithTechProfile(), usages(),
                changed, ARTEMIS_COMMIT);

        assertThat(findings).allMatch(item -> item.subject().equals("tech-a/build/agents"));
        assertThat(findings).extracting(ReportItem::message).anyMatch(message -> message.contains("visibility")).anyMatch(message -> message.contains("evidence"));
    }

    private FeatureScopeManifest manifest() {
        return manifest(List.of(new ConstraintEntry("alpha-requires-tech-a", "requires", "alpha", "tech-a", "Alpha needs tech A.")));
    }

    private FeatureScopeManifest manifest(List<ConstraintEntry> constraints) {
        List<FeatureEntry> declarations = List.of(declaration("alpha", "alpha-group"));
        List<ConceptualNode> conceptualNodes = List.of(new ConceptualNode("root", null, "root", null, null, null, null, "Root", null),
                new ConceptualNode("alpha-group", "root", "group", null, null, null, 1, "Alpha Group", null),
                new ConceptualNode("always-on", "alpha-group", "module", "mandatory", null, null, 2, "Always On", null),
                new ConceptualNode("tech-group", "root", "group", null, "technical", "alternative", 2, "Tech Group", null));
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, declarations, List.of(), List.of(), conceptualNodes, constraints, List.of());
    }

    private FeatureEntry declaration(String id, String group) {
        return new FeatureEntry(id, group, null, null, null, null, null, List.of(), null, null, null, null);
    }

    private List<ResolvedFeatureScope> includes() {
        MappingHint alphaHint = new MappingHint("application-feature-model.yml", "artemis.alpha.url", "environment", null, null, null);
        MappingHint techHint = new MappingHint(".env", "SPRING_PROFILES_ACTIVE", "selection", "tech-a-profile", null, null);
        return List.of(
                new ResolvedFeatureScope("module:alpha", "alpha", "alpha-group", null, "module", "optional", null, null, 1, List.of("alpha-service"), List.of(),
                        List.of(alphaHint), List.of(), null, null, null, "features"),
                new ResolvedFeatureScope("infra:tech-a", "tech-a", "tech-group", null, "feature", "optional", "technical", "enabled", 1, List.of(), List.of(),
                        List.of(techHint), List.of(), "Tech A", "Technical alternative A.", null, "technical"),
                new ResolvedFeatureScope("infra:tech-b", "tech-b", "tech-group", null, "feature", "optional", "technical", "disabled", 2, List.of(), List.of(),
                        List.of(), List.of(), "Tech B", "Technical alternative B.", null, "technical"));
    }

    private List<FeatureCandidate> candidates() {
        return List.of(new FeatureCandidate("module:alpha", FeatureCandidate.KIND_MODULE_FEATURE, "Alpha From I18n", "Alpha description.", null,
                "artemis.alpha.enabled", Boolean.TRUE, "ALPHA_ENABLED_PROPERTY_NAME", "MODULE_FEATURE_ALPHA", "AlphaEnabled", null, true, true, null),
                new FeatureCandidate("infra:tech-a", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null, null, null, null),
                new FeatureCandidate("infra:tech-b", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    private List<FeatureCandidate> candidatesWithTechProfile() {
        return List.of(candidates().getFirst(),
                new FeatureCandidate("infra:tech-a", FeatureCandidate.KIND_SPRING_PROFILE, null, null, null, null, null, "PROFILE_TECH_A", null, null, "tech-a", null,
                        null, null),
                candidates().getLast());
    }

    private List<ExtractedFeatureUsage> usages() {
        return List.of(
                new ExtractedFeatureUsage("src/main/java/de/tum/cit/aet/artemis/alpha/web/AlphaChatResource.java", 57, "alpha", "AlphaChatResource",
                        "chat/chat-sessions", List.of(), List.of("AlphaEnabled"), List.of()),
                new ExtractedFeatureUsage("src/main/java/de/tum/cit/aet/artemis/alpha/web/AlphaItemsResource.java", 10, "alpha", "AlphaItemsResource",
                        "authoring/items", List.of(), List.of("AlphaEnabled"), List.of()),
                new ExtractedFeatureUsage("src/main/java/de/tum/cit/aet/artemis/tech/web/TechAgentResource.java", 12, "tech", "TechAgentResource",
                        "build/agents", List.of(), List.of(), List.of("PROFILE_TECH_A")),
                new ExtractedFeatureUsage("src/main/java/de/tum/cit/aet/artemis/core/web/CoreResource.java", 3, "core", "CoreResource", "management/core",
                        List.of(), List.of(), List.of("PROFILE_CORE")));
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
