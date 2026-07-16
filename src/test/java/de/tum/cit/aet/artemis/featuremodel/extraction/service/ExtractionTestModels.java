package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;

/**
 * Synthetic in-memory curated models and catalogs for extraction tests, matching the mini-Artemis fixture.
 */
final class ExtractionTestModels {

    private ExtractionTestModels() {
    }

    /**
     * Creates the curated model used against the mini-Artemis fixture: one matched anchored feature with one moved
     * evidence reference, one anchored feature whose anchor no longer exists, and one unanchored root.
     *
     * @return synthetic curated model.
     */
    static FeatureModel fixtureCuratedModel() {
        FeatureNode root = new FeatureNode("fixture-root", "Fixture Root", "root", false, null, "not_applicable", null);
        FeatureNode alpha = new FeatureNode("alpha-feature", "Alpha", "module", true, null, "enabled", new FeatureSource("artemis.alpha.enabled", null,
                "MODULE_FEATURE_ALPHA", "AlphaEnabled", List.of("AlphaEnabled.java:12", "application-core.yml:2-3", "Constants.java:999")));
        FeatureNode ghost = new FeatureNode("ghost", "Ghost", "module", true, null, "disabled",
                new FeatureSource("artemis.ghost.enabled", null, null, "GhostEnabled", List.of("GhostEnabled.java:10")));
        return new FeatureModel(new ModelMetadata("fixture-model", "Fixture Model", "0.0.1"), List.of(root, alpha, ghost), List.of(), List.of());
    }

    /**
     * Creates the config key catalog used against the mini-Artemis fixture: one key still declared, one enabled key
     * gone from Artemis, one value key observed in the YAML defaults, and one value key not observed anywhere.
     *
     * @return synthetic catalog.
     */
    static ArtemisConfigKeyCatalog fixtureCatalog() {
        return new ArtemisConfigKeyCatalog("0.0.1-test", "fixturepin", "synthetic", List.of(
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.alpha.enabled", ArtemisConfigKeyCatalog.TYPE_BOOLEAN),
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.ghost.enabled", ArtemisConfigKeyCatalog.TYPE_BOOLEAN),
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.alpha.url", ArtemisConfigKeyCatalog.TYPE_URL),
                new ArtemisConfigKeyCatalog.CatalogKey("artemis.absent.url", ArtemisConfigKeyCatalog.TYPE_URL)));
    }

    /**
     * Creates a minimal curated model with only an unanchored root, for tests that do not exercise drift matching.
     *
     * @return minimal curated model.
     */
    static FeatureModel minimalCuratedModel() {
        FeatureNode root = new FeatureNode("fixture-root", "Fixture Root", "root", false, null, "not_applicable", null);
        return new FeatureModel(new ModelMetadata("fixture-model", "Fixture Model", "0.0.1"), List.of(root), List.of(), List.of());
    }
}
