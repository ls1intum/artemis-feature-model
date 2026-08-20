package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Layered readiness of a remote-ansible package, serialized into {@code metadata/remote-readiness.json}. The layers
 * make explicit what the generation proved and what remains an admin or execution-plane responsibility: the package
 * is consumable, never deployable. Dual-axis provenance records both the model identity and the binding-catalog
 * identity, because the two version independently.
 *
 * @param selectionValidated whether the selection passed validation; always {@code pass} in a generated package.
 * @param bindingsResolved per-feature classification results against the binding catalog.
 * @param valuesGenerated whether the inventory values were generated; always {@code pass} in a generated package.
 * @param environmentProvided per-input environment states: {@code provided} or {@code pending}.
 * @param secretsAsReferences whether secrets appear only as vault references; always {@code pass} by construction.
 * @param syntaxValidated always {@code pending}; the shipped preflight script performs the syntax check.
 * @param model identity of the model the package was generated from.
 * @param bindingCatalog identity of the binding catalog the package was generated with.
 */
public record RemoteReadinessReport(String selectionValidated, List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> bindingsResolved,
        String valuesGenerated, List<RemoteAnsibleEmissionPlan.EnvironmentState> environmentProvided, String secretsAsReferences, String syntaxValidated,
        ModelIdentity model, CatalogIdentity bindingCatalog) {

    /** Layer state of a proven generation step. */
    public static final String STATE_PASS = "pass";

    /** Layer state of a step deferred to the package consumer. */
    public static final String STATE_PENDING = "pending";

    /**
     * Normalizes nullable collections to immutable empty lists.
     *
     * @param selectionValidated selection layer state.
     * @param bindingsResolved classification results.
     * @param valuesGenerated values layer state.
     * @param environmentProvided environment states.
     * @param secretsAsReferences secrets layer state.
     * @param syntaxValidated syntax layer state.
     * @param model model identity.
     * @param bindingCatalog binding catalog identity.
     */
    public RemoteReadinessReport {
        bindingsResolved = bindingsResolved == null ? List.of() : List.copyOf(bindingsResolved);
        environmentProvided = environmentProvided == null ? List.of() : List.copyOf(environmentProvided);
    }

    /**
     * Identity of the generating model.
     *
     * @param id feature model id.
     * @param version feature model version.
     */
    public record ModelIdentity(String id, String version) {
    }

    /**
     * Identity of the binding catalog.
     *
     * @param catalogVersion catalog version.
     * @param collectionPin pinned collection commit the catalog was curated against.
     */
    public record CatalogIdentity(int catalogVersion, String collectionPin) {
    }
}
