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
 * @param requiredEnvironmentVariables environment-variable names the execution environment must provide; the shipped
 *            preflight script refuses a missing or empty variable.
 * @param secretsAsReferences whether secrets appear only as environment lookup expressions; always {@code pass} by
 *            construction.
 * @param syntaxValidated always {@code pending}; the shipped preflight script performs the syntax check.
 * @param model identity of the model the package was generated from.
 * @param bindingCatalog identity of the binding catalog the package was generated with.
 */
public record RemoteReadinessReport(String selectionValidated, List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> bindingsResolved,
        String valuesGenerated, List<String> requiredEnvironmentVariables, String secretsAsReferences, String syntaxValidated,
        ModelIdentity model, RemoteAnsibleEmissionPlan.CatalogIdentity bindingCatalog) {

    /** Layer state of a proven generation step. */
    public static final String STATE_PASS = "pass";

    /** Layer state of a step deferred to the package consumer. */
    public static final String STATE_PENDING = "pending";

    /**
     * Identity of the generating model.
     *
     * @param id feature model id.
     * @param version feature model version.
     */
    public record ModelIdentity(String id, String version) {
    }
}
