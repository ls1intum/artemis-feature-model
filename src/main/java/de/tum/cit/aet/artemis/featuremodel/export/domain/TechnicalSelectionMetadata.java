package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Serializable technical-selection summary recorded by Stage 1 without changing package behavior.
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

    /** Stage 1 records technical choices but leaves all runtime writers unchanged. */
    public static final String DISPOSITION_RECORDED_NOT_CONSUMED = "recorded-not-consumed-stage-1";

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
     * Builds Stage 1 recording metadata from a non-empty resolved technical selection.
     *
     * @param selection resolved technical selection.
     * @return recording metadata, or {@code null} when the selection is empty.
     */
    public static TechnicalSelectionMetadata from(TechnicalSelection selection) {
        if (selection.isEmpty()) {
            return null;
        }
        String databaseDisposition = selection.databaseId().isPresent() ? DISPOSITION_RECORDED_NOT_CONSUMED : null;
        boolean hasCiSelection = selection.ciProviderId().isPresent() || !selection.springProfileTokens().isEmpty();
        String ciDisposition = hasCiSelection ? DISPOSITION_RECORDED_NOT_CONSUMED : null;
        return new TechnicalSelectionMetadata(selection.databaseId().orElse(null), selection.databaseComposeFile().orElse(null), databaseDisposition,
                selection.ciProviderId().orElse(null), selection.springProfileTokens(), ciDisposition);
    }
}
