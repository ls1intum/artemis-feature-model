package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Serializable technical-selection summary and per-mode consumption disposition.
 *
 * @param databaseId selected database feature id.
 * @param databaseComposeFile selected database compose-file declaration.
 * @param databaseDisposition current handling of the database choice.
 * @param ciProviderId selected CI-provider feature id.
 * @param springProfileTokens selected Spring profile token declarations.
 * @param ciProviderDisposition current handling of the CI-provider and profile-token choices.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TechnicalSelectionMetadata(String databaseId, String databaseComposeFile, String databaseDisposition, String ciProviderId,
        List<String> springProfileTokens, String ciProviderDisposition) {

    /** The selected axis is applied by the package mode. */
    public static final String DISPOSITION_APPLIED = "applied";

    /** The database selection is recorded but remains developer-managed in dev-ide mode. */
    public static final String DISPOSITION_NOT_APPLICABLE_DEV_IDE = "not-applicable-dev-ide";

    /**
     * Normalizes profile tokens to an immutable list.
     *
     * @param databaseId selected database feature id.
     * @param databaseComposeFile selected database compose-file declaration.
     * @param databaseDisposition database handling disposition.
     * @param ciProviderId selected CI-provider feature id.
     * @param springProfileTokens selected profile tokens.
     * @param ciProviderDisposition CI-provider handling disposition.
     */
    public TechnicalSelectionMetadata {
        springProfileTokens = springProfileTokens == null ? List.of() : List.copyOf(springProfileTokens);
    }

    /**
     * Builds mode-specific metadata from a non-empty resolved technical selection.
     *
     * @param selection resolved technical selection.
     * @param databaseDisposition disposition for a selected database.
     * @param ciDisposition disposition for a selected CI provider.
     * @return technical metadata, or {@code null} when the selection is empty.
     */
    public static TechnicalSelectionMetadata from(TechnicalSelection selection, String databaseDisposition, String ciDisposition) {
        if (selection.isEmpty()) {
            return null;
        }
        String selectedDatabaseDisposition = selection.databaseId().isPresent() ? databaseDisposition : null;
        boolean hasCiSelection = selection.ciProviderId().isPresent() || !selection.springProfileTokens().isEmpty();
        String selectedCiDisposition = hasCiSelection ? ciDisposition : null;
        return new TechnicalSelectionMetadata(selection.databaseId().orElse(null), selection.databaseComposeFile().orElse(null),
                selectedDatabaseDisposition, selection.ciProviderId().orElse(null), selection.springProfileTokens(), selectedCiDisposition);
    }
}
