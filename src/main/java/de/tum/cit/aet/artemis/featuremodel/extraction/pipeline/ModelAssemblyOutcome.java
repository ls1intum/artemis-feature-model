package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Generated artifacts of one model assembly, handed from the model stage to the artifact store. Not serialized; the
 * persisted model-stage contract is the envelope in {@code extraction.domain}.
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
public record ModelAssemblyOutcome(List<ResolvedFeatureScope> includedFeatures, CurationReport curation, ManifestConformance conformance,
        FeatureModel generatedModel, ArtemisConfigKeyCatalog generatedCatalog, boolean generatedOutputConformant, boolean modelIntegrityValid,
        boolean deliveryEligible, List<ReportItem> items) {
}
