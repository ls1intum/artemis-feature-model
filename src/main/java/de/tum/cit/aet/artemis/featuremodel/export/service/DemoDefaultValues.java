package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.net.URI;
import java.net.URISyntaxException;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Derives and validates the visibly fake demo defaults DEMO artifacts use for environment requirements. Catalog-keyed
 * requirements receive a value typed by the config-key catalog: {@code url} keys get the RFC 2606 reserved
 * {@code https://feature-model-demo.invalid} that never resolves, {@code boolean} keys get {@code false}, and every
 * other type gets {@code demo-change-me}. Package-only requirements have no catalog identity to validate against and
 * always keep {@code demo-change-me}. A catalog-keyed requirement without a catalog entry, or a demo value that does
 * not match its catalog type, is an export-time error.
 */
final class DemoDefaultValues {

    /** Non-resolving demo URL for catalog type {@code url}; {@code .invalid} is RFC 2606 reserved. */
    static final String DEMO_URL = "https://feature-model-demo.invalid";

    /** Visibly fake demo value for non-URL, non-boolean requirements. */
    static final String DEMO_PLACEHOLDER = "demo-change-me";

    private DemoDefaultValues() {
    }

    /**
     * Derives the validated demo default of an environment requirement.
     *
     * @param requirement environment requirement.
     * @return typed demo value.
     * @throws ArtifactGenerationException if a catalog-keyed requirement has no catalog entry.
     */
    static String valueFor(EnvironmentRequirement requirement) {
        if (!requirement.isCatalogKeyed()) {
            return DEMO_PLACEHOLDER;
        }
        if (requirement.catalogType() == null) {
            throw ArtifactGenerationException.missingCatalogEntryForDemoDefault(requirement.configKey());
        }
        String value = switch (requirement.catalogType()) {
            case ArtemisConfigKeyCatalog.TYPE_URL -> DEMO_URL;
            case ArtemisConfigKeyCatalog.TYPE_BOOLEAN -> "false";
            default -> DEMO_PLACEHOLDER;
        };
        validate(requirement, value);
        return value;
    }

    /**
     * Validates a demo value against the catalog type of a catalog-keyed requirement. Package-only requirements are
     * exempt by construction because they have no catalog identity.
     *
     * @param requirement environment requirement.
     * @param value demo value to validate.
     * @throws ArtifactGenerationException if the value does not match the catalog type.
     */
    static void validate(EnvironmentRequirement requirement, String value) {
        if (!requirement.isCatalogKeyed()) {
            return;
        }
        if (requirement.catalogType() == null) {
            throw ArtifactGenerationException.missingCatalogEntryForDemoDefault(requirement.configKey());
        }
        boolean acceptable = switch (requirement.catalogType()) {
            case ArtemisConfigKeyCatalog.TYPE_URL -> isAbsoluteHttpUrl(value);
            case ArtemisConfigKeyCatalog.TYPE_BOOLEAN -> "true".equals(value) || "false".equals(value);
            default -> value != null && !value.isBlank();
        };
        if (!acceptable) {
            throw ArtifactGenerationException.invalidDemoDefault(requirement.configKey(), requirement.catalogType(), value);
        }
    }

    /**
     * Checks whether a string is an absolute http or https URL with a host.
     *
     * @param text string value.
     * @return true if the string parses as an absolute http(s) URL.
     */
    private static boolean isAbsoluteHttpUrl(String text) {
        try {
            URI uri = new URI(text);
            boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            return httpScheme && uri.getHost() != null;
        }
        catch (URISyntaxException e) {
            return false;
        }
    }
}
