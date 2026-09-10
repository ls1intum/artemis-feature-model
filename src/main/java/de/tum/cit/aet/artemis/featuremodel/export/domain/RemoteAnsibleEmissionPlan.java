package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Deterministic emission plan of a remote-ansible generation run: the rendered inventory files, every environment
 * reference they contain, and the per-feature classification results. The plan is produced by a pure function over
 * the active model, the selection, the binding catalog, and the resolved target identity; it contains no timestamps
 * and no secret or environment values.
 *
 * @param targetGroup inventory group name of the deployment target.
 * @param valuesFiles rendered inventory files in package order.
 * @param envReferences environment references contained in the rendered files, in file order.
 * @param classifications per-feature classification results in model order.
 * @param bindingCatalog identity of the binding catalog the plan was produced with.
 */
public record RemoteAnsibleEmissionPlan(String targetGroup, List<PlannedFile> valuesFiles, List<PlannedEnvReference> envReferences,
        List<FeatureClassificationResult> classifications, CatalogIdentity bindingCatalog) {

    /** Environment-reference kind of an admin-owned identity value. */
    public static final String ENV_KIND_IDENTITY = "identity";

    /** Environment-reference kind of a secret-class value. */
    public static final String ENV_KIND_SECRET = "secret";

    /**
     * Normalizes nullable collections to immutable empty lists.
     *
     * @param targetGroup inventory group name.
     * @param valuesFiles rendered inventory files.
     * @param envReferences contained environment references.
     * @param classifications per-feature classification results.
     * @param bindingCatalog binding catalog identity.
     */
    public RemoteAnsibleEmissionPlan {
        valuesFiles = valuesFiles == null ? List.of() : List.copyOf(valuesFiles);
        envReferences = envReferences == null ? List.of() : List.copyOf(envReferences);
        classifications = classifications == null ? List.of() : List.copyOf(classifications);
    }

    /**
     * Derives the sorted, de-duplicated environment-variable names the rendered files reference. This is the
     * fail-closed input of the shipped preflight env gate and the readiness environment section.
     *
     * @return sorted required environment-variable names.
     */
    public List<String> requiredEnvironmentVariables() {
        TreeSet<String> names = new TreeSet<>();
        for (PlannedEnvReference reference : envReferences) {
            names.add(reference.envVar());
        }
        return new ArrayList<>(names);
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
     * One environment reference contained in a rendered file.
     *
     * @param envVar user-provisioned environment-variable name.
     * @param consumer collection variable path that consumes the resolved value.
     * @param file package-relative path of the file containing the reference.
     * @param kind {@link #ENV_KIND_SECRET} or {@link #ENV_KIND_IDENTITY}.
     */
    public record PlannedEnvReference(String envVar, String consumer, String file, String kind) {
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
     * Identity of the binding catalog: its version and the pinned collection commit it was curated against.
     *
     * @param catalogVersion catalog version.
     * @param collectionPin pinned collection commit.
     */
    public record CatalogIdentity(int catalogVersion, String collectionPin) {
    }
}
