package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the source facts of one scan into the generated artifacts the manifest describes: it applies manifest
 * membership to the extracted candidates, assembles the generated feature model and the regenerated config key
 * catalog, validates the model-side rules, and classifies every difference from the curated model. It never reopens
 * the Artemis checkout — everything it needs comes from the scan artifacts and the manifest.
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
     * @param generatedModel assembled generated feature model.
     * @param generatedCatalog regenerated config key catalog.
     * @param modelDiff classified generated-versus-curated diff report.
     * @param modelIntegrityValid whether the generated model passed the shared structural integrity validation.
     * @param items model assembly diagnostics.
     */
    record Outcome(List<ResolvedFeatureScope> includedFeatures, CurationReport curation, FeatureModel generatedModel, ArtemisConfigKeyCatalog generatedCatalog,
            ModelDiffReport modelDiff, boolean modelIntegrityValid, List<ReportItem> items) {
    }

    /**
     * Assembles the generated artifacts of one run.
     *
     * @param manifest loaded scope manifest.
     * @param scan source facts of the consumed scan.
     * @param curatedModel active curated model for the diff comparison.
     * @param catalog curated config key catalog for the diff comparison.
     * @param bundledProfile bundled deployment profile for the capability cross-check.
     * @param artemisCommit resolved commit the scan was taken from.
     * @return generated artifacts and diagnostics.
     */
    Outcome assemble(FeatureScopeManifest manifest, FeatureExtractionService.Outcome scan, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog,
            DeploymentProfile bundledProfile, String artemisCommit) {
        List<ReportItem> items = new ArrayList<>();
        ScopeCurationService.Result curation = new ScopeCurationService().curate(manifest, scan.candidates(), scan.annotations());
        items.addAll(curation.items());

        GeneratedModelAssembler.Result generated = new GeneratedModelAssembler(objectMapper).assemble(manifest, curation.includedFeatures(), scan.candidates(),
                scan.evidence(), scan.relationCandidates(), artemisCommit);
        items.addAll(generated.items());

        GeneratedCatalogAssembler catalogAssembler = new GeneratedCatalogAssembler();
        GeneratedCatalogAssembler.Result generatedCatalog = catalogAssembler.assemble(generated.model(), scan.configDefaults(), artemisCommit);
        items.addAll(generatedCatalog.items());

        GeneratedModelValidator.Result validation = new GeneratedModelValidator().validate(generated.model(), curation.includedFeatures(), bundledProfile);
        items.addAll(validation.items());

        ModelDiffReport modelDiff = new ModelDiffService().compare(curatedModel, generated.model(), catalogAssembler.diff(catalog, generatedCatalog.catalog()),
                artemisCommit);
        return new Outcome(curation.includedFeatures(), curation.report(), generated.model(), generatedCatalog.catalog(), modelDiff,
                validation.modelIntegrityValid(), List.copyOf(items));
    }
}
