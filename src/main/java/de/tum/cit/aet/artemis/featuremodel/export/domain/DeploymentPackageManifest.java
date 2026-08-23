package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Package-level metadata serialized into {@code metadata/package-manifest.json}. It records what the deployment
 * package contains, the deployment mode it was generated for, and which runtime modes it supports, so a user (or a
 * tool) can inspect the package without unzipping and reading every file.
 *
 * <p>
 * The {@link #deploymentMode} field records an explicitly chosen deployment mode and is omitted when the caller relied
 * on the default, so the default local-docker package keeps its mode-axis recording behavior. The local Docker
 * package advertises both its local-repository and remote-image runtime capabilities through
 * {@link #supportedRuntimeModes}. The manifest never contains plaintext secrets: secret values appear elsewhere only as {@code ${VARIABLE}}
 * placeholders.
 *
 * @param packageType stable package type identifier.
 * @param packageVersion package format version.
 * @param mode generation mode; only {@code DEMO} is supported.
 * @param deploymentMode explicitly chosen deployment mode id, or {@code null} (omitted) for a default-mode request.
 * @param supportedRuntimeModes runtime modes this package can be started in.
 * @param model active feature model reference.
 * @param deploymentProfile active deployment profile reference.
 * @param artemisRuntime Artemis runtime context notes relevant to the generated package.
 * @param database local runtime database used to validate startup, or {@code null} for packages without a runtime.
 * @param ciProvider selected CI provider, or {@code null} for packages without a technical selection.
 * @param technicalSelection selected technical axes and their mode-specific disposition, or {@code null}.
 * @param generatedFiles relative paths of all files in the package, in deterministic order.
 * @param requiredEnvironmentVariables environment variable names the overlay references, from {@code .env.example}.
 * @param readiness readiness flags making clear this is a local-validation, non-production package.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentPackageManifest(String packageType, String packageVersion, String mode, String deploymentMode, List<String> supportedRuntimeModes,
        ModelRef model, ProfileRef deploymentProfile, ArtemisRuntimeInfo artemisRuntime, Database database, CiProvider ciProvider,
        TechnicalSelectionMetadata technicalSelection, List<String> generatedFiles, List<String> requiredEnvironmentVariables, Readiness readiness) {

    /**
     * Normalizes nullable list fields to immutable empty lists.
     *
     * @param packageType stable package type identifier.
     * @param packageVersion package format version.
     * @param mode generation mode.
     * @param deploymentMode explicitly chosen deployment mode id, or {@code null}.
     * @param supportedRuntimeModes supported runtime modes.
     * @param model active feature model reference.
     * @param deploymentProfile active deployment profile reference.
     * @param artemisRuntime Artemis runtime context notes.
     * @param database local runtime database, or {@code null}.
     * @param ciProvider selected CI provider, or {@code null}.
     * @param technicalSelection selected technical axes, or {@code null}.
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
     * Creates a manifest without technical-selection metadata for models without structural technical mappings.
     *
     * @param packageType stable package type identifier.
     * @param packageVersion package format version.
     * @param mode generation mode.
     * @param deploymentMode explicitly chosen deployment mode id, or {@code null}.
     * @param supportedRuntimeModes supported runtime modes.
     * @param model active feature model reference.
     * @param deploymentProfile active deployment profile reference.
     * @param artemisRuntime Artemis runtime context notes.
     * @param database local runtime database, or {@code null}.
     * @param generatedFiles package file paths.
     * @param requiredEnvironmentVariables referenced environment variable names.
     * @param readiness readiness flags.
     */
    public DeploymentPackageManifest(String packageType, String packageVersion, String mode, String deploymentMode, List<String> supportedRuntimeModes,
            ModelRef model, ProfileRef deploymentProfile, ArtemisRuntimeInfo artemisRuntime, Database database, List<String> generatedFiles,
            List<String> requiredEnvironmentVariables, Readiness readiness) {
        this(packageType, packageVersion, mode, deploymentMode, supportedRuntimeModes, model, deploymentProfile, artemisRuntime, database, null, null,
                generatedFiles, requiredEnvironmentVariables, readiness);
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
     * Artemis runtime provenance recorded for generated packages.
     *
     * @param sourceCommit Artemis source commit associated with the runtime package.
     * @param imageRepository official Artemis application image repository.
     * @param imageDigest original configured image digest, or the special value {@code latest}.
     * @param note human-readable runtime provenance note.
     */
    public record ArtemisRuntimeInfo(String sourceCommit, String imageRepository, String imageDigest, String note) {
    }

    /**
     * Local runtime database used for validation.
     *
     * @param type database type, for example {@code mysql}.
     * @param mode how the database is provided, for example {@code local-container}.
     */
    public record Database(String type, String mode) {
    }

    /**
     * Selected continuous-integration provider.
     *
     * @param type selected CI-provider feature id.
     * @param mode how the package applies the provider.
     */
    public record CiProvider(String type, String mode) {
    }

    /**
     * Readiness flags for the generated package.
     *
     * @param localRuntimeReady whether the package can be used for local runtime validation.
     * @param productionReady always {@code false}; no generated package is production-ready.
     * @param reason human-readable reason the package is not production-ready.
     */
    public record Readiness(boolean localRuntimeReady, boolean productionReady, String reason) {
    }
}
