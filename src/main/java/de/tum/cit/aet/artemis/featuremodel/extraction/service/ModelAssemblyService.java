package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the source facts of one scan into the generated artifacts the manifest describes: it applies manifest
 * membership to the extracted candidates, gates on complete manifest conformance, assembles the generated feature
 * model and the regenerated config key catalog, and validates the model-side rules. A run whose curation is incomplete stops at the gate and produces diagnostics only, so no model
 * can silently omit what the manifest never decided about. It never reopens the Artemis checkout — everything it
 * needs comes from the scan artifacts and the manifest.
 */
class ModelAssemblyService {

    private final ObjectMapper objectMapper;

    /**
     * Creates the model assembly service.
     *
     * @param objectMapper Jackson mapper shared with the generated model assembler.
     */
    ModelAssemblyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generated artifacts of one model assembly.
     *
     * @param includedFeatures resolved included semantics sorted by candidate id.
     * @param curation manifest classification section.
     * @param conformance verdict on whether the manifest describes the scanned source completely.
     * @param generatedModel assembled generated feature model, or null when conformance failed.
     * @param generatedCatalog regenerated config key catalog, or null when conformance failed.
     * @param generatedOutputConformant whether the generated model exactly matches the resolved manifest semantics.
     * @param modelIntegrityValid whether the generated model passed the shared structural integrity validation.
     * @param deliveryEligible whether every model, catalog, and profile delivery gate passed.
     * @param items model assembly diagnostics.
     */
    record Outcome(List<ResolvedFeatureScope> includedFeatures, CurationReport curation, ManifestConformance conformance, FeatureModel generatedModel,
            ArtemisConfigKeyCatalog generatedCatalog, boolean generatedOutputConformant, boolean modelIntegrityValid, boolean deliveryEligible,
            List<ReportItem> items) {
    }

    /**
     * Assembles the generated artifacts of one run.
     *
     * @param manifest loaded scope manifest.
     * @param scan source facts of the consumed scan.
     * @param bundledProfile bundled deployment profile for the capability cross-check.
     * @param artemisCommit resolved commit the scan was taken from.
     * @return generated artifacts and diagnostics.
     */
    Outcome assemble(FeatureScopeManifest manifest, ExtractedSourceFacts scan, DeploymentProfile bundledProfile, String artemisCommit) {
        List<ReportItem> items = new ArrayList<>();
        ScopeCurationService.Result curation = new ScopeCurationService().curate(manifest, scan.candidates(), scan.annotations());
        items.addAll(curation.items());
        ManifestConformanceService.Result conformance = new ManifestConformanceService().evaluate(manifest, curation.includedFeatures(),
                scan.relationCandidates(), curation.report(), curation.items(), scan.items());
        items.addAll(conformance.items());
        if (!conformance.conformance().conformant()) {
            return new Outcome(curation.includedFeatures(), curation.report(), conformance.conformance(), null, null, false, false, false, List.copyOf(items));
        }

        GeneratedModelAssembler.Result generated = new GeneratedModelAssembler(objectMapper).assemble(manifest, curation.includedFeatures(), scan.candidates(),
                scan.evidence(), artemisCommit);
        items.addAll(generated.items());

        GeneratedCatalogAssembler.Result generatedCatalog = new GeneratedCatalogAssembler().assemble(generated.model(), scan.configDefaults(), artemisCommit);
        items.addAll(generatedCatalog.items());

        List<ReportItem> generatedOutputFindings = new GeneratedModelConformanceService(objectMapper).validate(manifest, curation.includedFeatures(),
                scan.candidates(), generated.model(), artemisCommit);
        items.addAll(generatedOutputFindings);

        GeneratedModelValidator.Result validation = new GeneratedModelValidator().validate(generated.model(), curation.includedFeatures(), bundledProfile);
        items.addAll(validation.items());

        boolean catalogEligible = generatedCatalog.items().stream().noneMatch(item -> ReportItem.SEVERITY_ERROR.equals(item.severity()));
        return new Outcome(curation.includedFeatures(), curation.report(), conformance.conformance(), generated.model(), generatedCatalog.catalog(),
                generatedOutputFindings.isEmpty(), validation.modelIntegrityValid(),
                validation.deliveryEligible() && catalogEligible && generatedOutputFindings.isEmpty(), List.copyOf(items));
    }
}
