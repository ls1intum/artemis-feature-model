package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import java.util.Collection;

import org.springframework.http.HttpStatus;

/**
 * Controlled exception for artifact generation failures exposed through the artifact generation API. Carries a stable
 * code and the HTTP status the caller should receive, so an invalid selection is reported as a bad request rather than
 * as an opaque server fault. Missing deployment profiles are reported through {@link DeploymentProfileException}.
 */
public class ArtifactGenerationException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    /**
     * Creates an artifact generation exception.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @param status HTTP status the caller should receive.
     */
    public ArtifactGenerationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Creates a bad-request exception for a selection that fails validation.
     *
     * @param violationCount number of validation violations.
     * @param firstViolationMessage message of the first violation, for a readable hint.
     * @return invalid-selection artifact generation exception.
     */
    public static ArtifactGenerationException invalidSelection(int violationCount, String firstViolationMessage) {
        String message = "Cannot generate artifacts: the selection is invalid (" + violationCount + " violation(s)). " + firstViolationMessage;
        return new ArtifactGenerationException("ARTIFACT_GENERATION_INVALID_SELECTION", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a deployment mode id that is not a known mode.
     *
     * @param deploymentMode requested deployment mode id.
     * @return unknown-deployment-mode artifact generation exception.
     */
    public static ArtifactGenerationException unknownDeploymentMode(String deploymentMode) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNKNOWN_DEPLOYMENT_MODE", "Unknown deployment mode '" + deploymentMode + "'.",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a known deployment mode the active profile does not support.
     *
     * @param deploymentMode requested deployment mode id.
     * @param profileId active deployment profile id.
     * @return unsupported-deployment-mode artifact generation exception.
     */
    public static ArtifactGenerationException unsupportedDeploymentMode(String deploymentMode, String profileId) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNSUPPORTED_DEPLOYMENT_MODE",
                "Deployment mode '" + deploymentMode + "' is not supported by deployment profile '" + profileId + "'.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a selected structural mapping this stage does not recognize.
     *
     * @param featureId mapping owner.
     * @param target mapping target.
     * @param path mapping path.
     * @return unsupported technical-mapping exception.
     */
    public static ArtifactGenerationException unsupportedTechnicalMapping(String featureId, String target, String path) {
        String message = "Feature '" + featureId + "' declares unsupported technical mapping '" + target + ":" + path + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNSUPPORTED_TECHNICAL_MAPPING", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a structural mapping without a usable selected text value.
     *
     * @param featureId mapping owner.
     * @param target mapping target.
     * @param path mapping path.
     * @return invalid technical-mapping value exception.
     */
    public static ArtifactGenerationException invalidTechnicalMappingValue(String featureId, String target, String path) {
        String message = "Feature '" + featureId + "' must declare a non-blank text value for technical mapping '" + target + ":" + path + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_INVALID_TECHNICAL_MAPPING", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception when more than one selected feature owns the same technical axis.
     *
     * @param axis technical axis label.
     * @param currentValue first selected owner or value.
     * @param nextValue conflicting selected owner or value.
     * @return conflicting technical-selection exception.
     */
    public static ArtifactGenerationException conflictingTechnicalSelection(String axis, String currentValue, String nextValue) {
        String message = "Technical selection has conflicting " + axis + " values '" + currentValue + "' and '" + nextValue + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_CONFLICTING_TECHNICAL_SELECTION", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a profile-token set with no supported writer-owned ordering.
     *
     * @param tokens resolved technical profile tokens.
     * @return unsupported profile-token exception.
     */
    public static ArtifactGenerationException unsupportedTechnicalProfileTokens(Collection<String> tokens) {
        String message = "Technical selection declares unsupported Spring profile tokens: " + String.join(", ", tokens) + ".";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNSUPPORTED_TECHNICAL_PROFILE_TOKENS", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a resolved technical choice no writer supports.
     *
     * @param axis technical axis.
     * @param value resolved value.
     * @return unsupported technical-choice exception.
     */
    public static ArtifactGenerationException unsupportedTechnicalChoice(String axis, String value) {
        String message = "Technical selection declares unsupported " + axis + " choice '" + value + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNSUPPORTED_TECHNICAL_CHOICE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for missing local-docker runtime provenance.
     *
     * @param sourceLabel actionable label identifying the missing snapshot field or classpath property.
     * @return missing runtime provenance exception.
     */
    public static ArtifactGenerationException missingArtemisRuntimeValue(String sourceLabel) {
        String message = "Cannot generate a local-docker package: missing required Artemis runtime value " + sourceLabel + ".";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_MISSING_ARTEMIS_RUNTIME_VALUE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a selection state the pinned Ansible collection cannot express.
     *
     * @param featureId feature whose selection state is inexpressible.
     * @param detail missing-variable or scope reason from the binding catalog.
     * @return unsupported remote-ansible feature exception.
     */
    public static ArtifactGenerationException remoteAnsibleUnsupportedFeature(String featureId, String detail) {
        String message = "Cannot generate a remote-ansible package: feature '" + featureId + "' is not expressible with the pinned collection. " + detail;
        return new ArtifactGenerationException("ARTIFACT_GENERATION_REMOTE_ANSIBLE_UNSUPPORTED_FEATURE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for an active-model feature the Ansible binding catalog does not classify. This
     * is the run-time coverage gate: a model ahead of the application's catalog is refused instead of silently
     * under-configured.
     *
     * @param featureId unclassified feature id.
     * @param catalogVersion binding catalog version.
     * @param collectionPin binding catalog collection pin.
     * @return unclassified remote-ansible feature exception.
     */
    public static ArtifactGenerationException remoteAnsibleUnclassifiedFeature(String featureId, int catalogVersion, String collectionPin) {
        String message = "Cannot generate a remote-ansible package: Ansible binding catalog v" + catalogVersion + " @ collection pin " + collectionPin
                + " does not classify feature '" + featureId + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_REMOTE_ANSIBLE_UNCLASSIFIED_FEATURE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a remote-ansible selection without a required technical choice.
     *
     * @param axis technical axis label.
     * @return missing technical-choice exception.
     */
    public static ArtifactGenerationException remoteAnsibleMissingTechnicalChoice(String axis) {
        String message = "Cannot generate a remote-ansible package: the selection resolves no " + axis + " choice.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_REMOTE_ANSIBLE_MISSING_TECHNICAL_CHOICE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a remote environment component supplied on a non-remote deployment mode.
     *
     * @param deploymentMode resolved deployment mode of the request.
     * @return not-applicable remote environment exception.
     */
    public static ArtifactGenerationException remoteEnvironmentNotApplicable(String deploymentMode) {
        String message = "The remoteEnvironment request component applies only to deployment mode 'remote-ansible', not to '" + deploymentMode + "'.";
        return new ArtifactGenerationException("ARTIFACT_GENERATION_REMOTE_ENVIRONMENT_NOT_APPLICABLE", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns the stable error code for this failure.
     *
     * @return error code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the HTTP status the caller should receive.
     *
     * @return HTTP status.
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Creates the exception for a catalog-keyed environment requirement whose configuration key is absent from the
     * active config-key catalog, so no typed demo default can be derived for it.
     *
     * @param configKey configuration key missing from the catalog.
     * @return exception describing the missing catalog entry.
     */
    public static ArtifactGenerationException missingCatalogEntryForDemoDefault(String configKey) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_MISSING_CATALOG_ENTRY",
                "Configuration key '" + configKey + "' has no entry in the Artemis config key catalog; a typed demo default cannot be derived.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Creates the exception for a demo default whose value does not match the catalog type of its configuration key.
     *
     * @param configKey configuration key of the requirement.
     * @param catalogType catalog type the value must satisfy.
     * @param value rejected demo value.
     * @return exception describing the type mismatch.
     */
    public static ArtifactGenerationException invalidDemoDefault(String configKey, String catalogType, String value) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_INVALID_DEMO_DEFAULT",
                "Demo default '" + value + "' for configuration key '" + configKey + "' does not match catalog type '" + catalogType + "'.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
