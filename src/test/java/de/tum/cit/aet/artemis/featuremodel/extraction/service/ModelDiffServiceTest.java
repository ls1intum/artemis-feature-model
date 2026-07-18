package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport.DiffEntry;

/** Covers the difference classification: every diff entry carries exactly one class and an explanation. */
class ModelDiffServiceTest {

    private final ModelDiffService service = new ModelDiffService();

    @Test
    void classifiesEveryDifferenceWithExplanation() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(report.entries()).isNotEmpty();
        assertThat(report.entries()).allSatisfy(entry -> {
            assertThat(entry.classification()).isIn(ModelDiffReport.CLASS_INTENTIONAL_CURATION, ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY,
                    ModelDiffReport.CLASS_ARTEMIS_DRIFT, ModelDiffReport.CLASS_EXTRACTOR_GAP);
            assertThat(entry.explanation()).isNotBlank();
        });
        int classifiedTotal = report.classificationCounts().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(classifiedTotal).isEqualTo(report.entries().size());
    }

    @Test
    void classifiesNameDifferenceAsIntentionalCuration() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(entry(report, "alpha", "feature-name").classification()).isEqualTo(ModelDiffReport.CLASS_INTENTIONAL_CURATION);
    }

    @Test
    void classifiesDefaultStateDifferenceAsArtemisDrift() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(entry(report, "alpha", "feature-default-state").classification()).isEqualTo(ModelDiffReport.CLASS_ARTEMIS_DRIFT);
    }

    @Test
    void classifiesCapabilityDifferenceAsMissingManifestEntry() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(entry(report, "alpha", "feature-requires-capabilities").classification()).isEqualTo(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY);
    }

    @Test
    void classifiesTechnicalAdditionsAsIntentionalCuration() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(entry(report, "tech-a", "feature-membership").classification()).isEqualTo(ModelDiffReport.CLASS_INTENTIONAL_CURATION);
        assertThat(entry(report, "root->tech-a", "relation").classification()).isEqualTo(ModelDiffReport.CLASS_INTENTIONAL_CURATION);
        assertThat(entry(report, "tech-a-excludes-missing", "constraint").classification()).isEqualTo(ModelDiffReport.CLASS_INTENTIONAL_CURATION);
    }

    @Test
    void classifiesCuratedFeatureWithoutGeneratedCounterpartAsMissingManifestEntry() {
        ModelDiffReport report = service.compare(curatedModel(), generatedModel(), emptyCatalogDiff(), "0123456789abcdef");

        assertThat(entry(report, "curated-only", "feature-membership").classification()).isEqualTo(ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY);
        assertThat(report.classificationCounts()).containsEntry(ModelDiffReport.CLASS_EXTRACTOR_GAP, 0);
    }

    private DiffEntry entry(ModelDiffReport report, String subject, String aspect) {
        return report.entries().stream().filter(candidate -> candidate.subject().equals(subject) && candidate.aspect().equals(aspect)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing diff entry for " + subject + " / " + aspect));
    }

    private FeatureModel curatedModel() {
        FeatureNode root = new FeatureNode("root", "Root", "root", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha", "Alpha Curated", "module", true, null, "enabled", null, "functional", List.of("teacher", "maintainer"),
                List.of("teacher", "maintainer"), List.of("alpha-service"), null, null);
        FeatureNode curatedOnly = new FeatureNode("curated-only", "Curated Only", "module", true, null, "enabled", null);
        List<FeatureRelation> relations = List.of(new FeatureRelation("root", "alpha", "optional", null, 1),
                new FeatureRelation("root", "curated-only", "optional", null, 2));
        return new FeatureModel(new ModelMetadata("curated-model", "Curated Model", "0.1.0"), List.of(root, alpha, curatedOnly), relations, List.of());
    }

    private FeatureModel generatedModel() {
        FeatureNode root = new FeatureNode("root", "Root", "root", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha", "Alpha From I18n", "module", true, null, "disabled", null, "functional", List.of("teacher", "maintainer"),
                List.of("teacher", "maintainer"), List.of(), null, null);
        FeatureNode techA = new FeatureNode("tech-a", "Tech A", "feature", true, null, "enabled", null, "technical", List.of("maintainer"),
                List.of("maintainer"), List.of(), null, null);
        List<FeatureRelation> relations = List.of(new FeatureRelation("root", "alpha", "optional", null, 1), new FeatureRelation("root", "tech-a", "optional", null, 2));
        FeatureConstraint techConstraint = new FeatureConstraint("tech-a-excludes-missing", "excludes", "tech-a", "alpha", null, "Synthetic exclusivity.");
        return new FeatureModel(new ModelMetadata("generated-model", "Generated Model", "0.1.0+0123456789ab"), List.of(root, alpha, techA), relations,
                List.of(techConstraint));
    }

    private ModelDiffReport.CatalogDiff emptyCatalogDiff() {
        return new ModelDiffReport.CatalogDiff("1.0.0", "oldpin", "0123456789abcdef", List.of(), List.of(), List.of());
    }
}
