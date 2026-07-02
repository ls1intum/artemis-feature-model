package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Package-level metadata serialized into {@code metadata/package-manifest.json}. It records what the local runtime
 * deployment package contains and which runtime modes it supports, so a user (or a later phase) can inspect the package
 * without unzipping and reading every file.
 *
 * <p>
 * This phase supports Layer 1 (local Artemis repository runtime) only; {@link #supportedRuntimeModes} reflects that.
 * The manifest never contains plaintext secrets: secret values appear elsewhere only as {@code ${VARIABLE}}
 * placeholders.
 *
 * @param packageType stable package type identifier.
 * @param packageVersion package format version.
 * @param mode generation mode; only {@code DEMO} in this phase.
 * @param supportedRuntimeModes runtime modes this package can be started in.
 * @param model active feature model reference.
 * @param deploymentProfile active deployment profile reference.
 * @param artemisRuntime Artemis runtime context notes relevant to the local-repo layer.
 * @param generatedFiles relative paths of all files in the package, in deterministic order.
 * @param requiredEnvironmentVariables environment variable names the overlay references, from {@code .env.example}.
 * @param readiness readiness flags making clear this is a local-validation, non-production package.
 */
public record DeploymentPackageManifest(String packageType, String packageVersion, String mode, List<String> supportedRuntimeModes, ModelRef model,
        ProfileRef deploymentProfile, ArtemisRuntimeInfo artemisRuntime, List<String> generatedFiles, List<String> requiredEnvironmentVariables, Readiness readiness) {

    /**
     * Normalizes nullable list fields to immutable empty lists.
     *
     * @param packageType stable package type identifier.
     * @param packageVersion package format version.
     * @param mode generation mode.
     * @param supportedRuntimeModes supported runtime modes.
     * @param model active feature model reference.
     * @param deploymentProfile active deployment profile reference.
     * @param artemisRuntime Artemis runtime context notes.
     * @param generatedFiles package file paths.
     * @param requiredEnvironmentVariables referenced environment variable names.
     * @param readiness readiness flags.
     */
    public DeploymentPackageManifest {
        supportedRuntimeModes = supportedRuntimeModes == null ? List.of() : List.copyOf(supportedRuntimeModes);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        requiredEnvironmentVariables = requiredEnvironmentVariables == null ? List.of() : List.copyOf(requiredEnvironmentVariables);
    }

    /**
     * Identifying reference to the active feature model.
     *
     * @param id feature model id.
     * @param version feature model version.
     */
    public record ModelRef(String id, String version) {
    }

    /**
     * Identifying reference to the active deployment profile.
     *
     * @param id deployment profile id.
     * @param version deployment profile version.
     */
    public record ProfileRef(String id, String version) {
    }

    /**
     * Artemis runtime context recorded for the local-repo layer.
     *
     * @param verifiedAgainstArtemisCommit abbreviated Artemis commit the overlay keys were verified against.
     * @param note human-readable note about how the local-repo layer reuses the local Artemis stack.
     */
    public record ArtemisRuntimeInfo(String verifiedAgainstArtemisCommit, String note) {
    }

    /**
     * Readiness flags for the generated package.
     *
     * @param localRuntimeReady whether the package can be used for local runtime validation.
     * @param productionReady always {@code false} in this phase.
     * @param reason human-readable reason the package is not production-ready.
     */
    public record Readiness(boolean localRuntimeReady, boolean productionReady, String reason) {
    }
}
