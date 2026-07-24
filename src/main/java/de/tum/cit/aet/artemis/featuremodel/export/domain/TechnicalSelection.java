package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;
import java.util.Optional;

/**
 * Technical feature choices resolved from structural artifact mappings.
 *
 * @param springProfileTokens Spring profile token contributions in feature-model order.
 * @param databaseComposeFile selected database compose file.
 * @param databaseId feature id that owns the selected database mapping.
 * @param ciProviderId feature id that owns the selected CI-provider mapping.
 */
public record TechnicalSelection(List<String> springProfileTokens, Optional<String> databaseComposeFile, Optional<String> databaseId,
        Optional<String> ciProviderId) {

    /**
     * Normalizes collections and optionals to immutable non-null values.
     *
     * @param springProfileTokens Spring profile token contributions.
     * @param databaseComposeFile selected database compose file.
     * @param databaseId owning database feature id.
     * @param ciProviderId owning CI-provider feature id.
     */
    public TechnicalSelection {
        springProfileTokens = springProfileTokens == null ? List.of() : List.copyOf(springProfileTokens);
        databaseComposeFile = databaseComposeFile == null ? Optional.empty() : databaseComposeFile;
        databaseId = databaseId == null ? Optional.empty() : databaseId;
        ciProviderId = ciProviderId == null ? Optional.empty() : ciProviderId;
    }

    /**
     * Creates an empty technical selection for models without structural technical mappings.
     *
     * @return empty technical selection.
     */
    public static TechnicalSelection empty() {
        return new TechnicalSelection(List.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Checks whether no technical mapping contributed a value.
     *
     * @return true when the selection has no resolved technical data.
     */
    public boolean isEmpty() {
        return springProfileTokens.isEmpty() && databaseComposeFile.isEmpty() && databaseId.isEmpty() && ciProviderId.isEmpty();
    }
}
