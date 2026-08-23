package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Deterministic emission plan of a remote-ansible generation run: the rendered inventory files, every vault reference
 * they contain, the per-feature classification results, and the per-input environment states. The plan is produced by
 * a pure function over the active model, the selection, the binding catalog, and the resolved environment values; it
 * contains no timestamps and no secret values.
 *
 * @param targetGroup inventory group name of the deployment target.
 * @param valuesFiles rendered inventory files in package order.
 * @param vaultReferences vault references contained in the rendered files, in file order.
 * @param classifications per-feature classification results in model order.
 * @param environmentStates per-input environment states in input declaration order.
 * @param bindingCatalog identity of the binding catalog the plan was produced with.
 */
public record RemoteAnsibleEmissionPlan(String targetGroup, List<PlannedFile> valuesFiles, List<PlannedVaultReference> vaultReferences,
        List<FeatureClassificationResult> classifications, List<EnvironmentState> environmentStates, CatalogIdentity bindingCatalog) {

    /** Environment state of a provided (or derived) input. */
    public static final String ENVIRONMENT_PROVIDED = "provided";

    /** Environment state of an input resolved to a placeholder. */
    public static final String ENVIRONMENT_PENDING = "pending";

    /**
     * Normalizes nullable collections to immutable empty lists.
     *
     * @param targetGroup inventory group name.
     * @param valuesFiles rendered inventory files.
     * @param vaultReferences contained vault references.
     * @param classifications per-feature classification results.
     * @param environmentStates per-input environment states.
     * @param bindingCatalog binding catalog identity.
     */
    public RemoteAnsibleEmissionPlan {
        valuesFiles = valuesFiles == null ? List.of() : List.copyOf(valuesFiles);
        vaultReferences = vaultReferences == null ? List.of() : List.copyOf(vaultReferences);
        classifications = classifications == null ? List.of() : List.copyOf(classifications);
        environmentStates = environmentStates == null ? List.of() : List.copyOf(environmentStates);
    }

    /**
     * One rendered inventory file.
     *
     * @param path package-relative file path.
     * @param content rendered file content.
     */
    public record PlannedFile(String path, String content) {
    }

    /**
     * One vault reference contained in a rendered file.
     *
     * @param path resolved vault path.
     * @param field field name inside the vault secret.
     * @param consumer collection variable path that consumes the resolved value.
     * @param file package-relative path of the file containing the reference.
     */
    public record PlannedVaultReference(String path, String field, String consumer, String file) {
    }

    /**
     * Classification result of one selectable feature against the binding catalog.
     *
     * @param featureId feature id.
     * @param classification catalog classification of the feature.
     * @param selected whether the feature is part of the selection.
     */
    public record FeatureClassificationResult(String featureId, String classification, boolean selected) {
    }

    /**
     * Environment state of one remote environment input.
     *
     * @param input input name.
     * @param status {@link #ENVIRONMENT_PROVIDED} or {@link #ENVIRONMENT_PENDING}.
     */
    public record EnvironmentState(String input, String status) {
    }

    /**
     * Identity of the binding catalog: its version and the pinned collection commit it was curated against.
     *
     * @param catalogVersion catalog version.
     * @param collectionPin pinned collection commit.
     */
    public record CatalogIdentity(int catalogVersion, String collectionPin) {
    }
}
